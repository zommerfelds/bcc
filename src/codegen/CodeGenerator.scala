package codegen

import ast._
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.File

object CodeGenerator {

  def iffalse(expr: Expression, label: X86Label)(implicit current: List[Int], params: List[String], pathList: List[List[Int]], cus: List[CompilationUnit]): List[X86Instruction] = {
    expr.generateCode ::: (X86Mov(X86ebx, X86Boolean(false)) :: X86Cmp(X86eax, X86ebx) :: X86Je(label) :: Nil) //TODO:eax contains answer?
  }
  
  private def makeLabel(p: Option[Name], c: ClassDefinition, s: String) = {
    (p match {
      case Some(x) => x + "."
      case None => ""
    }) + c.className + "." + s
  }
  
  def makeFieldLabel(p: Option[Name], c: ClassDefinition, f: FieldDeclaration) = {
    makeLabel(p, c, f.fieldName)
  }

  def makeMethodLabel(p: Option[Name], c: ClassDefinition, m: MethodDeclaration) = {
    makeLabel(p, c, m.methodName + "$" +
      m.parameters.map(_.paramType.typeName.replaceAllLiterally("[]", "$")).mkString("_"))
  }

  /**
   * generate files in the output/ directory
   */
  def makeAssembly(cus: List[CompilationUnit]): Unit = {
    // DUMMY CODE
	/*
	val writer = new BufferedWriter(new FileWriter(new File("output/simple.s")))
	writer.write("""
	
	global _start
	_start:
	
	mov eax, 1
	mov ebx, 123
	int 0x80
	
	          
	""")
	writer.close
	*/
    
    def generate(cu: CompilationUnit, cd: ClassDefinition, isFirst: Boolean)(implicit cus:List[CompilationUnit]): String = { //we just need the CU for the full name
      
      def getMethods(pkg: Option[Name], cd: ClassDefinition, parentMethods: List[(Option[Name], ClassDefinition, MethodDeclaration)]): List[(Option[Name], ClassDefinition, MethodDeclaration)] = {

		def methodsMatch(m1: MethodDeclaration, m2: MethodDeclaration): Boolean = {
		  m1.methodName == m2.methodName && m1.parameters == m2.parameters
		}

        def mergeMethods(ms: List[MethodDeclaration], ts: List[(Option[Name], ClassDefinition, MethodDeclaration)]): List[(Option[Name], ClassDefinition, MethodDeclaration)] = {
          ms match {
            case Nil => ts
            case m :: mss =>
              mergeMethods(mss, ts.find(t => methodsMatch(m, t._3)) match {
                case None => (pkg, cd, m) :: ts
                case Some(x) => x :: ts.filter(t => methodsMatch(m, t._3))
              })   
          }
        }
        
        val replaced = mergeMethods(cd.methods, parentMethods)
        cd.parent match {
          case None => replaced
          case Some(p) => 
            val linked = p.asInstanceOf[RefTypeLinked]
            getMethods(linked.pkgName, linked.getTypeDef(cus).asInstanceOf[ClassDefinition], replaced)
        }
      }
      val methods = getMethods(cu.packageName, cd, Nil)
            
      ///////////////// header ///////////////////////
      val header =
        "extern __malloc\n" +
        "extern __exception\n" +
        "extern __debexit\n" +
        methods.filterNot(t => t._1 == cu.packageName && t._2 == cd)
               .map(t => s"extern ${makeMethodLabel(t._1, t._2, t._3)}")
               .mkString("\n") +
        "\n\n"
      ///////////////// end of header/////////////////
      
      ///////////////// data segment /////////////////
      val data =
        "section .data\n\n" +
        "; VTABLE\n" +
        "class:\n" + 
        "dd 0 ; TODO: pointer to SIT\n" +
        methods.map(t => s"dd ${makeMethodLabel(t._1, t._2, t._3)}")
               .mkString("\n  ") +
        "\n\n"
      ///////////////// end of data segment /////////

      ///////////////// bss segment /////////////////
      val staticFields = cd.fields.filter(x => x.modifiers.contains(Modifier.staticModifier))
      val bss =
        "section .bss\n\n" +
        "; static fields\n" +
        staticFields.map(f => s"${makeFieldLabel(cu.packageName, cd, f)}: resb 4").mkString("\n") + "\n\n"
      ///////////////// end of bss segment //////////
  
      ///////////////// text segment /////////////////
      val text =
        "section .text\n\n" +
        "global " + makeLabel(cu.packageName, cd, ".static_init") + "\n" +
        makeLabel(cu.packageName, cd, ".static_init") + ":\n" +
        staticFields.map(f =>
          "  ; " + f.fieldName + "\n" +
          (f.initializer match {
            case Some(expr) => expr.generateCode2.mkString("\n") +
                               s"\n  mov [${makeFieldLabel(cu.packageName, cd, f)}], eax"
            case None => ""
          })).mkString("\n") +
        "\nret\n\n" +	
        "global " + makeLabel(cu.packageName, cd, ".alloc") + "\n" +
        makeLabel(cu.packageName, cd, ".alloc") + ":\n" +
        ";mov eax, x\n" +
        "call __malloc\n" +
        "mov [eax], dword class ; set pointer to class\n" +
        "ret\n\n" +
        cd.methods.map(m => {
          val lbl = makeMethodLabel(cu.packageName, cd, m)
          val mainFunc = (isFirst && m.methodName == "test" && m.parameters == Nil)
          "global " + lbl + "\n" +
          lbl + ":\n" +
          (
            if (mainFunc) "global _start\n_start:\n"
            else ""
          ) +
          m.generateCode.map(i => i match {
            case X86Ret => X86Jmp(X86Label("__debexit"))
            case x => x
          }).mkString("\n")
        }).mkString("\n\n")
      ///////////////// end of text segment //////////

      "; === " + cd.className + "===\n" + header + data + bss + text
    }
    
    cus
    //leave the java lib files out for the moment! -> makes testing easier
    //.filter(_.packageName != Some(Name("java"::"lang"::Nil))).filter(_.packageName != Some(Name("java"::"io"::Nil))).filter(_.packageName != Some(Name("java"::"util"::Nil)))
    .collect { case cu @ CompilationUnit(optName, _, Some(d: ClassDefinition), name) =>
      val writer = new BufferedWriter(new FileWriter(new File("output/"+cu.typeName+".s")))
        //println("class: " + cu.typeName)
      val code = generate(cu, d, isFirst = (cu == cus.head))(cus)
      writer.write(code)
      writer.close
    }
  }
}
