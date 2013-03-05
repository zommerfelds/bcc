package ast

abstract class Type extends AstNode{
    def typeName:String
}

abstract class PrimitiveType extends Type {
  val children = Nil
}

object PrimitiveType{
  def fromString(str: String): PrimitiveType = str match{
    case "int" => IntType
    case "boolean" => BooleanType
    case "byte" => ByteType
    case "short" => ShortType
    case "char" => CharType
    case "void" => VoidType
  }
}

case object IntType extends PrimitiveType{
    def typeName: String = "int"
}

case object BooleanType extends PrimitiveType{
    def typeName: String = "boolean"
}

case object ByteType extends PrimitiveType{
    def typeName: String = "byte"
}

case object ShortType extends PrimitiveType{
    def typeName: String = "short"
}

case object CharType extends PrimitiveType{
    def typeName: String = "char"
}

case object VoidType extends PrimitiveType{
  def typeName: String = "void"
}

case class ArrayType(elementType: Type) extends Type{
  def typeName: String = elementType.typeName + "[]"
  val children = elementType :: Nil
}

abstract class RefType(path:Name) extends Type {
    def typeName: String = path.toString
    val children = Nil
}

case class RefTypeUnlinked(path: Name) extends RefType(path) {
}

class RefTypeLinked(val path: Name, typeDef: =>TypeDefinition) extends RefType(path) {
  lazy val decl = typeDef
}


object RefTypeLinked{
  def apply(path: Name, typeDef: => TypeDefinition) = new RefTypeLinked(path, typeDef)
  def unapply(refType: RefTypeLinked) = Some((refType.path, refType.decl))
}
