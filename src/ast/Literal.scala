package ast

import scanner.IntegerToken
import scanner.StringToken
import scanner.CharacterToken
import codegen._

abstract class Literal extends Expression

case class NumberLiteral(int: Int) extends Literal{
  def getType(implicit cus: List[CompilationUnit], isStatic: Boolean, myType: RefTypeLinked): Type = IntType
  override def generateCode() = X86Mov(X86eax, X86Number(int)) :: Nil
}

case object NullLiteral extends Literal{
  def getType(implicit cus: List[CompilationUnit], isStatic: Boolean, myType: RefTypeLinked): Type = NullType
  override def generateCode() = X86Comment("null:") :: X86Mov(X86eax, X86Number(0)) :: Nil //null -> zero
}

case class BooleanLiteral(bool: Boolean) extends Literal{
  def getType(implicit cus: List[CompilationUnit], isStatic: Boolean, myType: RefTypeLinked): Type = BooleanType
  override def generateCode() = X86Comment("boolean:") :: X86Mov(X86eax, X86Boolean(bool)) :: Nil
}

case class CharacterLiteral(char: CharacterToken) extends Literal{
  def getType(implicit cus: List[CompilationUnit], isStatic: Boolean, myType: RefTypeLinked): Type = CharType
  override def generateCode() = X86Comment("character:") :: X86Mov(X86eax, X86Number(char.getInt)) :: Nil
}

case class StringLiteral(str: StringToken) extends Literal{
  def getType(implicit cus: List[CompilationUnit], isStatic: Boolean, myType: RefTypeLinked): Type = RefTypeLinked(Some(Name(List("java", "lang"))), "String")
  override def generateCode() = ??? // __malloc and stuff X86Mov(X86eax, X86Number(char.getInt)) :: Nil
}

