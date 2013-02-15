package parser

import scanner.Token
import scanner.OperatorToken
import scanner.IdentifierToken


trait Symbol

case class NonTerminalSymbol(name: String, reducedSymbol: List[Symbol]) extends Symbol 
