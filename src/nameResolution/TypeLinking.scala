package nameResolution

import ast._
import main.Logger.debug

class EnvironmentException(message:String) extends main.CompilerError(message)

object TypeLinking {
  type NameMap = Map[Name, (Option[Name], String)]
  def treatAll(cus:List[CompilationUnit]):List[CompilationUnit] = {
    debug("CREATE POSSIBLE IMPORTS:")
    val possibleImports = getPossibleImports(cus)
    debug("TREAT FILES SEPARATELY:")
    cus.map(linkCompilationUnit(_, possibleImports))
  }
  def getPossibleImports(cus:List[CompilationUnit]):List[(Option[Name], String)] = {//:(Map[Name, TypeDefinition], Map[Name, List[(Name, TypeDefinition)]]) = { //pcknm -> List[ClassDecl]
    debug("GET POSSIBLE IMPORTS:")
    val names = cus.map(x => x.typeDef.get.getName)
    if (names.length != names.distinct.length)
      throw new EnvironmentException("two typedefinition given as arguments for joosc have the same name!");
    val possible = cus.filter(_.typeDef.isDefined).map(x => (x.packageName, x.typeName))
    possible.foreach(x => debug(x._1+"-"+x._2))
    possible
  }
  def linkCompilationUnit(cu:CompilationUnit, possibleImports:List[(Option[Name], String)]):CompilationUnit = {
    debug("LINKCOMPILATIONSUNIT:")
    def checkPrefix(name:Name, map:NameMap) {
      for (i <- (1 to name.path.length by 1)) { //everything except the full name
        if (map.get(Name(name.path.take(i))).isDefined)
          throw new EnvironmentException("Error: imported prefix: "+map.get(Name(name.path.take(i))).get)
      }
    }
    def importSelf(map:NameMap):NameMap = {
	    debug("IMPORT SELF")
	    val fullName = cu.packageName.getOrElse(Name(Nil)).appendClassName(cu.typeName)
	    map + (Name(cu.typeName::Nil) -> (cu.packageName, cu.typeName)) + (fullName -> (cu.packageName, cu.typeName)) //might overwrite the old one
    }
    def importClass(fullname:Name, map:NameMap):NameMap = {
      debug("IMPORT CLASS")
      val className = fullname.path.last
      val packageName = fullname match {case Name(x::Nil) => None case Name(xs) => Some(Name(xs.dropRight(1)))}//might be Name(Nil)!
      possibleImports.find(x => x._1 == packageName && x._2 == className) match {
        case Some(found) =>
        case None => throw new EnvironmentException("Error: not part of possible imports: "+fullname)
      }
      if (map.contains(Name(className::Nil))) //should never happen right now...
        throw new EnvironmentException("Error: imports with same name: "+className)
      map + (Name(className::Nil) -> (packageName, className)) + (fullname -> (packageName, className))
    }
    def importPackage(packageName:Name, map:NameMap):NameMap = {
      debug("IMPORT PACKAGE")
      def rec(className:String, map:NameMap):NameMap = {
        val fullName = packageName.appendClassName(className)
        if (map.contains(Name(className::Nil)))
          map + (fullName -> (Some(packageName), className))
        else
          map + (fullName -> (Some(packageName), className)) + (Name(className::Nil) -> (Some(packageName), className))
      }
      possibleImports.filter(_._1 == Some(packageName)).foldLeft(map)((m, i) => rec(i._2, m))
    }
    def importAll(list:List[ImportDeclaration]):NameMap = {
      debug("IMPORTALL")
      val classes = list.filter{case ClassImport(_) => true case _ => false}.map(_.getName)
      val packages = list.filter{case PackageImport(_) => true case _ => false}.map(_.getName)
      val mapSelf = importSelf(Map[Name, (Option[Name], String)]()) //NameMap() doesn't work
      val classImports = classes.foldLeft(mapSelf)((map:NameMap, y:Name) => importClass(y, map))
      val myPackage = 
        if (cu.packageName.isDefined)
          importPackage(cu.packageName.get, classImports);
        else
      		classImports
      val packageImports = packages.foldLeft(myPackage)((map:NameMap, y:Name) => importPackage(y, map))
      		
      debug("IMPORT SELF:")
      mapSelf.foreach(x => debug(x._1))
      debug("IMPORT CLASSES:")
      classImports.foreach(x => debug(x._1))
      debug("IMPORT MY PACKAGE:")
      myPackage.foreach(x => debug(x._1))
      debug("IMPORT PACKAGES:")
      packageImports.foreach(x => debug(x._1))
      
      val finalImport = importPackage(Name("java"::"lang"::Nil), packageImports)
      
      finalImport.foreach(x => x._2._1 match {case Some(pkgname) => checkPrefix(pkgname, packageImports) case None => })
      finalImport
    }
	def linkAst(cu:CompilationUnit, imported:NameMap, possibleImports:NameMap):CompilationUnit = {
	  debug("LINK AST:")
	    def linkCompilationUnit(cu:CompilationUnit):CompilationUnit = {
		  debug("LINK COMPILATIONUNIT:")
		    CompilationUnit(cu.packageName, cu.importDeclarations, cu.typeDef.map(linkTypeDefinition(_)), cu.typeName)
		  }
		  def linkTypeDefinition(td:TypeDefinition):TypeDefinition = td match {
		    case id:InterfaceDefinition =>debug("LINK INTERFACE:"); InterfaceDefinition(id.interfaceName, id.parents.map(link(_)), id.modifiers, id.methods.map(linkMethod(_)))
		    case cd:ClassDefinition =>debug("LINK CLASS:"); ClassDefinition(cd.className, cd.parent.map(link(_)), cd.interfaces.map(link(_)), cd.modifiers, cd.fields.map(linkField(_)), cd.constructors.map(linkConstructor(_)), cd.methods.map(linkMethod(_))) 
		  }
		  def linkMethod(md:MethodDeclaration):MethodDeclaration = {
		    debug("LINK METHOD:")
		    MethodDeclaration(md.methodName, link(md.returnType), md.modifiers, md.parameters.map(linkParameter(_)), md.implementation.map(linkBlock(_))) 
		  }
		  def linkField(fd:FieldDeclaration):FieldDeclaration = {
		    debug("LINK FIELD:")
		    FieldDeclaration(fd.fieldName, link(fd.fieldType), fd.modifiers, fd.initializer.map(linkExpression(_)))
		  }
		  def linkConstructor(cd:ConstructorDeclaration):ConstructorDeclaration = {
		    debug("LINK CONSTRUCTOR:")
		    ConstructorDeclaration(cd.modifiers, cd.parameters.map(linkParameter(_)), linkBlock(cd.implementation)) 
		  }
		  def linkParameter(parameter:Parameter):Parameter = {
		    debug("LINK PARAMETER:")
		    Parameter(link(parameter.paramType), parameter.id)
		  }
		  def linkBlock(block:Block):Block = { //separate linkBlock required for Constructor an other stuff
		    debug("LINK BLOCK:")
		    Block(block.statements.map(linkStatement(_)))
		  }
		  def linkStatement(s:Statement):Statement =  s match {
		    case Block(statements : List[Statement]) => Block(statements.map(linkStatement(_)))
		    case ExpressionStatement(expression: Expression) =>ExpressionStatement(linkExpression(expression))
		    case ForStatement(init, condition, incrementation, loop) => ForStatement(init.map(linkStatement(_)), condition.map(linkExpression(_)), incrementation.map(linkExpression(_)), linkStatement(loop))
		    case IfStatement(condition: Expression, ifStatement: Statement, elseStatement: Option[Statement]) => IfStatement(linkExpression(condition), linkStatement(ifStatement), elseStatement.map(linkStatement(_)))
		    case ReturnStatement(returnExpression) => ReturnStatement(returnExpression.map(linkExpression(_)))
		    case LocalVariableDeclaration(typeName: Type, identifier: String, initializer: Option[Expression]) => LocalVariableDeclaration(link(typeName), identifier, initializer.map(linkExpression(_)))
		    case WhileStatement(condition: Expression, loop: Statement) => WhileStatement(linkExpression(condition), linkStatement(loop))
		    case _ => s //any other case!
		  }
		  def linkExpression(e:Expression):Expression = e match {
		    case UnaryOperation(operation, term) => UnaryOperation(operation, linkExpression(term))
			case BinaryOperation(first, operation, second) => BinaryOperation(linkExpression(first), operation, linkExpression(second))
			case CastExpression(typeCast, target) => CastExpression(link(typeCast), linkExpression(target))
			case ArrayAccess(typeCast : Expression, target: Expression) => ArrayAccess(linkExpression(typeCast), linkExpression(target))
			case ArrayCreation(typeName : Type, size: Expression) => ArrayCreation(link(typeName), linkExpression(size))
			case Assignment(leftHandSide: Expression, rightHandSide: Expression) => Assignment(linkExpression(leftHandSide), linkExpression(rightHandSide))
			case ClassCreation(constructor: RefType, parameters: List[Expression]) => ClassCreation(link(constructor), parameters.map(linkExpression(_)))
			case FieldAccess(accessed : Expression, field: String) => FieldAccess(linkExpression(accessed), field)
			case MethodInvocation(accessed: Option[Expression], method : String, arguments: List[Expression]) => MethodInvocation(accessed.map(linkExpression(_)), method, arguments.map(linkExpression(_)))
			case InstanceOfCall(exp: Expression, typeChecked: Type) => InstanceOfCall(linkExpression(exp), link(typeChecked))
			case _ => e //return expression by default
		  }
		  def link[A <: Type](pt:A):A = pt match { //anything which is not RefType is simpletype
		    case RefTypeUnlinked(path) =>
		      debug("LINK REFERENCETYPE")
		      debug("to be found: "+path+" length:"+path.toString.length)
		      imported.get(path) match {
		        case Some(tuple) => RefTypeLinked(tuple._1, tuple._2).asInstanceOf[A]
		        case None => possibleImports.get(path) match {
		          case Some(tuple) => RefTypeLinked(tuple._1, tuple._2).asInstanceOf[A]//if not imported can still be used via direct name!
		          case None => throw new EnvironmentException(path+" not imported yet!")
		        }
		      }
		    case l:RefTypeLinked =>
		      debug("already linked! Why are you traversing twice?)");
		      l.asInstanceOf[A]
		    case ArrayType(elementType) => debug("arraytype -> no link:"); ArrayType(link(elementType)).asInstanceOf[A]
		    case x => debug("Simple type, no link:"); x
		  }
		  linkCompilationUnit(cu)
	}

	val possibleList = possibleImports.map(x => (x._1.getOrElse(Name(Nil)).appendClassName(x._2), (x._1, x._2)))
	val possible = possibleList.toMap
	if (possibleList.length != possible.size)
	  debug("Some stuff has been overwritten!")
    debug("all possible imports")
	possible.foreach(x => debug("name:"+x._1))
	//val imported = importList(cu.importDeclarations, Map[Name, TypeDefinition]())
	val imported = importAll(cu.importDeclarations)
	debug("to be imported: ")
	imported.foreach(x => debug("name:"+x._1))
    
	linkAst(cu, imported, possible)
  }
}
