package scanner

object Scanner {

    def checkEncoding(code: String) ={
        code.
        matches("\\p{ASCII}*")
    }

    def generateTokens(code:String):List[String] = {
		removeCommentsExtractStrings(code) //
		.toList
		.map(addSpace(_)) //add whitespace around special characters
		.flatten(splitCode(_)) //split at places indicated by separator-token
	}
	
	def removeCommentsExtractStrings(code:String):List[String] = {
		/*
	   * (?<=".*?")|(?=".*?") separate string groups without rejecting them -> set empty string as splitter
	   * ?: is for non capturing groups
	   * .* is for any character except line breaks
	   * (?m)$ matches the end of the line ($ just matches the end of the input)
	   * (?s). matches everything including line breaks
	   * * is the greedy version (maximum match)
	   * *? is the reluctant (non-greedy) version (minimum match)
	   * "�" is no longer in the range of 7-bit ascii characters so we can use it as a splipoint because it occurs nowhere else in the inplut file
	   */
		code.replaceAll("""(".*?")|(//.*(?m)$)|(/\*(?s).*?\*/)""", """�$1�""").split("""�""").toList.filter(!_.matches("""(//.*(?m)$)|(/\*(?s).*?\*/)""")) //((?<=".*?")|(?=".*?"))|//.*(?m)$|(?:/\*(?s).*?\*/)
	}
	
	def isString(line:String):Boolean = {
		line.matches("""(?:".*")""")
	}
	
	def addSpace(line:String): String = {
		//add space around special characters so they are easier to parse
		//this could be done in a cleaner way using "lookahead" and "lookbehind"
		if (isString(line)) line
		else line.replaceAll("""(\+\+|\-\-|<=|>=|==|!=|\|\||[\+\-\*/^\|&?!=<>\(\)\[\]\{\}\.,;:])""", """ $1 """)
	}
	
	def splitCode(line:String):List[String] = {
		//sperate code at whitespaces
		if (isString(line)) List(line)
		else line.replaceAll("""(^\s+)|(\s+$)""", "").split("""[\s\t\n\r\f\a\e]+""").filter(_ != "").toList
	}
	
	def addLineNumbers(code:String):String = {
	  var counter = 0;
	  code.replaceAll("((?m)^.*(?m)$)",(counter = counter+1).toString()+" $1")
	}

    def categorize(list : List[String]): List[Token] = {
        val identifiers = "[a-zA-Z\\$_][a-zA-Z0-9\\$_]*".r;

        val keywords = List("abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double", "else", "enum",
                "extends", "final", "finally", "float", "for", "if", "goto", "implements", "import",
                "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected",
                "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while");
                

        val parenthesis = "[\\(\\)\\[\\]\\{\\}]".r;
        val semicolon = ";".r;
        val integer = "(?:[1-9][\\d_]*)?\\d".r
        val string = "^\"(?s).*\"$".r
        val boolean = "true|fasle".r
        val char = "'.'".r
        

        list.map{
            _ match{
                case x if keywords contains x => KeywordToken(x)
                case identifiers(id) => IdentifierToken(id)
                case parenthesis(prt) => ScopingToken(prt)
                case semicolon(sm) => SemiColonToken(sm)
                case integer(intlit) => IntegerToken(intlit)
                case string(str) => StringToken(str)
            }
        }
    }
}
