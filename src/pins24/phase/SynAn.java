package pins24.phase;

import pins24.common.*;

/**
 * Sintaksni analizator.
 */
public class SynAn implements AutoCloseable {

	/** Leksikalni analizator. */
	private final LexAn lexAn;

	/**
	 * Ustvari nov sintaksni analizator.
	 * 
	 * @param srcFileName ime izvorne datoteke.
	 */
	public SynAn(final String srcFileName) {
		this.lexAn = new LexAn(srcFileName);
	}

	@Override
	public void close() {
		lexAn.close();
	}

	/**
	 * Prevzame leksikalni analizator od leksikalnega analizatorja in preveri, ali
	 * je prave vrste.
	 * 
	 * @param symbol Pricakovana vrsta leksikalnega simbola.
	 * @return Prevzeti leksikalni simbol.
	 */
	public Token check(Token.Symbol symbol) {
		final Token token = lexAn.takeToken();
		if (token.symbol() != symbol)
			throw new Report.Error(token, "Unexpected symbol '" + token.lexeme() + "'.");
		return token;
	}

	/**
	 * Opravi sintaksno analizo.
	 */
	public void parse() {
		parseProgram();
		if (lexAn.peekToken().symbol() != Token.Symbol.EOF)
			throw new Report.Error(lexAn.peekToken(), "Unexpected text '" + lexAn.peekToken().lexeme() + "...'.");
	}

	/**
	 * Opravi sintaksno analizo celega programa.
	 * program -> definition program2
	 */
	private void parseProgram() {
		parseDefinition();
		parseProgram2();
	}

	/**
	 * Continue parsing definitions if they exist.
	 * program2 -> definition program2 .
	 * program2 -> .
	 */
	private void parseProgram2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.VAR || lexAn.peekToken().symbol() == Token.Symbol.FUN) {
			parseDefinition();
		}
	}

	/**
	 * Parses a definition, which can be a variable or function declaration.
	 * definition -> VAR ID ASSIGN initializers .
	 * definition -> FUN ID LP parameters RP definition2 .
	 */
	private void parseDefinition() {
		switch (lexAn.peekToken().symbol()) {
			case VAR:
				check(Token.Symbol.VAR);
				check(Token.Symbol.IDENTIFIER);
				check(Token.Symbol.ASSIGN);
				parseInitializers();
				return;
			case FUN:
				check(Token.Symbol.FUN);
				check(Token.Symbol.IDENTIFIER);
				check(Token.Symbol.LPAREN);
				parseParameters();
				check(Token.Symbol.RPAREN);
				parseDefinition2();
				return;
			default:
				throw new Report.Error(lexAn.peekToken(), "A definition expected (VAR or FUN).");
		}
	}

	/**
	 * Continues parsing definitions if they exist.
	 * definition2 -> .
	 * definition2 -> ASSIGN statements .
	 */
	private void parseDefinition2() {
		if (lexAn.peekToken().symbol() == Token.Symbol.ASSIGN) {
			check(Token.Symbol.ASSIGN);
			parseStatements();
		}
	}

	/**
	 * Parses initializers.
	 * initializers -> .
	 * initializers -> initializer initializers2 .
	 */
	private void parseInitializers() {
		if (isConstant(lexAn.peekToken().symbol())) {
			parseInitializer();
			parseInitializers2();
		}
	}

	/**
	 * Continues parsing initializers if they exist.
	 * initializers2 -> .
	 * initializers2 -> COMMA initializer initializers2 .
	 */
	private void parseInitializers2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.COMMA) {
			check(Token.Symbol.COMMA);
			parseInitializer();
		}
	}

	/**
	 * Parses an initializer.
	 * initializer -> INTCONST initializer2 .
	 * initializer -> CHARCONST .
	 * initializer -> STRINGCONST .
	 */
	private void parseInitializer() {
		switch (lexAn.peekToken().symbol()) {
			case INTCONST:
				check(Token.Symbol.INTCONST);
				parseInitializer2();
				break;
			case CHARCONST:
				check(Token.Symbol.CHARCONST);
				break;
			case STRINGCONST:
				check(Token.Symbol.STRINGCONST);
				break;
			default:
				throw new Report.Error(lexAn.peekToken(), "An initializer expected (INTCONST, CHARCONST, or STRINGCONST).");
		}
	}

	/**
	 * Continues parsing initializers if they exist.
	 * initializer2 -> .
	 * initializer2 -> MUL const .
	 */
	private void parseInitializer2() {
		if (lexAn.peekToken().symbol() == Token.Symbol.MUL) {
			check(Token.Symbol.MUL);
			parseConst();
		}
	}

	/**
	 * Parses constants.
	 * const -> INTCONST .
	 * const -> CHARCONST .
	 * const -> STRINGCONST .
	 */
	private void parseConst() {
		Token token = lexAn.peekToken();
		if (isConstant(token.symbol())) {
			check(token.symbol());
		} else {
			throw new Report.Error(token, "A constant expected (INTCONST, CHARCONST, or STRINGCONST).");
		}
	}

	/**
	 * Parses parameters for a function declaration.
	 * parameters -> .
	 * parameters -> ID parameters2 .
	 */
	private void parseParameters() {
		if (lexAn.peekToken().symbol() == Token.Symbol.IDENTIFIER) {
			check(Token.Symbol.IDENTIFIER);
			parseParameters2();
		}
	}

	/**
	 * Continues parsing parameters if they exist.
	 * parameters2 -> .
	 * parameters2 -> COMMA ID parameters2 .
	 */
	private void parseParameters2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.COMMA) {
			check(Token.Symbol.COMMA);
			check(Token.Symbol.IDENTIFIER);
		}
	}

	/**
	 * Parses a sequence of statements.
	 * statements -> statement statements2 .
	 */
	private void parseStatements() {
		parseStatement();
		parseStatements2();
	}

	/**
	 * Parses additional statements if they exist.
	 * statements2 -> .
	 * statements2 -> COMMA statement statements2 .
	 */
	private void parseStatements2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.COMMA) {
			check(Token.Symbol.COMMA);
			parseStatement();
		}
	}

	/**
	 * Parses an individual statement based on the types defined.
	 * statement -> IF expression THEN statements statementIfElse END .
	 * statement -> WHILE expression DO statements END .
	 * statement -> LET definitions IN statements END .
	 * statement -> expression statement2 .
	 */
	private void parseStatement() {
		switch (lexAn.peekToken().symbol()) {
			case IF:
				parseIfStatement();
				break;
			case WHILE:
				parseWhileStatement();
				break;
			case LET:
				parseLetStatement();
				break;
			default:
				tryParseExpression("A statement expected.");
				parseStatement2();
				break;
		}
	}

	/**
	 * Continues parsing statements if they exist.
	 * statement2 -> .
	 * statement2 -> ASSIGN expression .
	 */
	private void parseStatement2() {
		if (lexAn.peekToken().symbol() == Token.Symbol.ASSIGN) {
			check(Token.Symbol.ASSIGN);
			parseExpression();
		}
	}

	/**
	 * Tries to parse an expression and throws appropriate error message if it fails.
	 */
	private void tryParseExpression(String errorMessage) {
		try {
			parseExpression();
		} catch (Report.Error error) {
			if (!error.getMessage().toLowerCase().contains("an expression expected (possibly a primary expression)")) {
				throw error;
			}
			throw new Report.Error(lexAn.peekToken(), errorMessage);
		}
	}

	/**
	 * Parses if-then-(else)-end statements.
	 * statement -> IF expression THEN statements statementIfElse END .
	 */
	private void parseIfStatement() {
		check(Token.Symbol.IF);
		tryParseExpression("An expression expected.");
		check(Token.Symbol.THEN);
		parseStatements();
		parseStatementIfElse();
		check(Token.Symbol.END);
	}

	/**
	 * Parses the optional else part of an if statement.
	 * statementIfElse -> .
	 * statementIfElse -> ELSE statements .
	 */
	private void parseStatementIfElse() {
		if (lexAn.peekToken().symbol() == Token.Symbol.ELSE) {
			check(Token.Symbol.ELSE);
			parseStatements();
		}
	}

	/**
	 * Parses while-do-end statements.
	 * statement -> WHILE expression DO statements END .
	 */
	private void parseWhileStatement() {
		check(Token.Symbol.WHILE);
		tryParseExpression("An expression expected.");
		check(Token.Symbol.DO);
		parseStatements();
		check(Token.Symbol.END);
	}

	/**
	 * Parses let-in-end statements for defining local scopes.
	 * statement -> LET definitions IN statements END .
	 */
	private void parseLetStatement() {
		check(Token.Symbol.LET);
		parseDefinitions();
		check(Token.Symbol.IN);
		parseStatements();
		check(Token.Symbol.END);
	}

	/**
	 * Parses a sequence of one or more definitions.
	 * definitions -> definition definitions2 .
	 */
	private void parseDefinitions() {
		parseDefinition();
		parseDefinitions2();
	}

	/**
	 * Continues parsing definitions if they exist.
	 * definitions2 -> .
	 * definitions2 -> definition definitions2 .
	 */
	private void parseDefinitions2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.VAR || lexAn.peekToken().symbol() == Token.Symbol.FUN) {
			parseDefinition();
		}
	}

	/**
	 * Parses a primary expression.
	 * primaryExpr -> INTCONST .
	 * primaryExpr -> CHARCONST .
	 * primaryExpr -> STRINGCONST .
	 * primaryExpr -> ID primaryExpr2 .
	 * primaryExpr -> LP expression RP .
	 */
	private void parsePrimaryExpr() {
		Token token = lexAn.peekToken();
		switch (token.symbol()) {
			case INTCONST:
			case CHARCONST:
			case STRINGCONST:
				check(token.symbol());
				break;
			case IDENTIFIER:
				check(Token.Symbol.IDENTIFIER);
				parsePrimaryExpr2();
				break;
			case LPAREN:
				check(Token.Symbol.LPAREN);
				parseExpression();
				check(Token.Symbol.RPAREN);
				break;
			default:
				throw new Report.Error(token, "An expression expected (possibly a primary expression).");
		}
	}

	/**
	 * Handles cases where an identifier is followed by a function call or nothing.
	 * primaryExpr2 -> .
	 * primaryExpr2 -> LP arguments RP .
	 */
	private void parsePrimaryExpr2() {
		if (lexAn.peekToken().symbol() == Token.Symbol.LPAREN) {
			check(Token.Symbol.LPAREN);
			parseArguments();
			check(Token.Symbol.RPAREN);
		}
	}

	/**
	 * Parses arguments inside a function call if they exist.
	 * arguments -> .
	 * arguments -> expression arguments2 .
	 */
	private void parseArguments() {
		if (isExpressionStart(lexAn.peekToken().symbol())) {
			parseExpression();
			parseArguments2();
		}
	}

	/**
	 * Continues parsing additional arguments if they exist.
	 * arguments2 -> .
	 * arguments2 -> COMMA expression arguments2 .
	 */
	private void parseArguments2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.COMMA) {
			check(Token.Symbol.COMMA);
			parseExpression();
		}
	}

	/**
	 * Parses postfix expressions.
	 * postfixExpr -> primaryExpr postfixExpr2 .
	 */
	private void parsePostfixExpr() {
		parsePrimaryExpr();
		parsePostfixExpr2();
	}

	/**
	 * Continues parsing postfix expressions if they exist.
	 * postfixExpr2 -> .
	 * postfixExpr2 -> PTR postfixExpr2 .
	 */
	private void parsePostfixExpr2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.PTR) {
			check(Token.Symbol.PTR);
			parsePostfixExpr2();
		}
	}

	/**
	 * Parses prefix expressions if they exist.
	 * prefixExpr -> postfixExpr .
	 * prefixExpr -> NOT prefixExpr .
	 * prefixExpr -> ADD prefixExpr .
	 * prefixExpr -> SUB prefixExpr .
	 * prefixExpr -> PTR prefixExpr .
	 */
	private void parsePrefixExpr() {
		Token token = lexAn.peekToken();
		switch (token.symbol()) {
			case NOT:
			case ADD:
			case SUB:
			case PTR:
				check(token.symbol());
				parsePrefixExpr();
				break;
			default:
				parsePostfixExpr();
				break;
		}
	}

	/**
	 * Parses multiplicative expressions.
	 * multExpr -> prefixExpr multExpr2 .
	 */
	private void parseMultExpr() {
		parsePrefixExpr();
		parseMultExpr2();
	}

	/**
	 * Continues parsing multiplicative expressions if they exist.
	 * multExpr2 -> .
	 * multExpr2 -> MUL prefixExpr multExpr2 .
	 * multExpr2 -> DIV prefixExpr multExpr2 .
	 * multExpr2 -> MOD prefixExpr multExpr2 .
	 */
	private void parseMultExpr2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.MUL || lexAn.peekToken().symbol() == Token.Symbol.DIV ||
				lexAn.peekToken().symbol() == Token.Symbol.MOD) {
			switch (lexAn.peekToken().symbol()) {
				case MUL:
					check(Token.Symbol.MUL);
					parsePrefixExpr();
					break;
				case DIV:
					check(Token.Symbol.DIV);
					parsePrefixExpr();
					break;
				case MOD:
					check(Token.Symbol.MOD);
					parsePrefixExpr();
					break;
			}
		}
	}

	/**
	 * Parses additive expressions.
	 * addExpr -> multExpr addExpr2 .
	 */
	private void parseAddExpr() {
		parseMultExpr();
		parseAddExpr2();
	}

	/**
	 * Continues parsing additive expressions if they exist.
	 * addExpr2 -> .
	 * addExpr2 -> ADD multExpr addExpr2 .
	 * addExpr2 -> SUB multExpr addExpr2 .
	 */
	private void parseAddExpr2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.ADD || lexAn.peekToken().symbol() == Token.Symbol.SUB) {
			switch (lexAn.peekToken().symbol()) {
				case ADD:
					check(Token.Symbol.ADD);
					parseMultExpr();
					break;
				case SUB:
					check(Token.Symbol.SUB);
					parseMultExpr();
					break;
			}
		}
	}

	/**
	 * Parses comparison expressions.
	 * compExpr -> addExpr compExpr2 .
	 */
	private void parseCompExpr() {
		parseAddExpr();
		parseCompExpr2();
	}

	/**
	 * Continues parsing comparison expressions if they exist.
	 * Chained comparisons without parentheses are not allowed.
	 * compExpr2 -> .
	 * compExpr2 -> EQU addExpr .
	 * compExpr2 -> NEQ addExpr .
	 * compExpr2 -> LTH addExpr .
	 * compExpr2 -> GTH addExpr .
	 * compExpr2 -> LEQ addExpr .
	 * compExpr2 -> GEQ addExpr .
	 */
	private void parseCompExpr2() {
		Token.Symbol symbol = lexAn.peekToken().symbol();
		if (isComparisonOperator(symbol)) {
			check(symbol);
			parseAddExpr();

			if (isComparisonOperator(lexAn.peekToken().symbol())) {
				throw new Report.Error(lexAn.peekToken(), "Chained comparisons without parentheses are not allowed.");
			}
		}
	}

	/**
	 * Parses logical AND expressions.
	 * andExpr -> compExpr andExpr2 .
	 */
	private void parseAndExpr() {
		parseCompExpr();
		parseAndExpr2();
	}

	/**
	 * Continues parsing logical AND expressions if they exist.
	 * andExpr2 -> .
	 * andExpr2 -> AND compExpr andExpr2 .
	 */
	private void parseAndExpr2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.AND) {
			check(Token.Symbol.AND);
			parseCompExpr();
		}
	}

	/**
	 * Parses logical OR expressions.
	 * orExpr -> andExpr orExpr2 .
	 */
	private void parseOrExpr() {
		parseAndExpr();
		parseOrExpr2();
	}

	/**
	 * Continues parsing logical OR expressions if they exist.
	 * orExpr2 -> .
	 * orExpr2 -> OR andExpr orExpr2 .
	 */
	private void parseOrExpr2() {
		while (lexAn.peekToken().symbol() == Token.Symbol.OR) {
			check(Token.Symbol.OR);
			parseAndExpr();
		}
	}

	/**
	 * Calls the top-level function to parse complete expressions.
	 * expression -> orExpr .
	 */
	private void parseExpression() {
		parseOrExpr();
	}

	private static final Token.Symbol[] CONSTANTS = { Token.Symbol.INTCONST, Token.Symbol.CHARCONST, Token.Symbol.STRINGCONST };
	private static final Token.Symbol[] PREFIX_OPERATORS = { Token.Symbol.NOT, Token.Symbol.ADD, Token.Symbol.SUB, Token.Symbol.PTR };
	private static final Token.Symbol[] COMPARISON_OPERATORS = { Token.Symbol.EQU, Token.Symbol.NEQ, Token.Symbol.LTH, Token.Symbol.GTH, Token.Symbol.LEQ, Token.Symbol.GEQ };

	private boolean isConstant(Token.Symbol symbol) {
		return isSymbolInArray(symbol, CONSTANTS);
	}

	private boolean isPrefixOperator(Token.Symbol symbol) {
		return isSymbolInArray(symbol, PREFIX_OPERATORS);
	}

	private boolean isComparisonOperator(Token.Symbol symbol) {
		return isSymbolInArray(symbol, COMPARISON_OPERATORS);
	}

	/**
	 * Determines if the current token can start an expression.
	 */
	private boolean isExpressionStart(Token.Symbol symbol) {
		Token.Symbol[] expressionStartSymbols = { Token.Symbol.INTCONST, Token.Symbol.CHARCONST, Token.Symbol.STRINGCONST, Token.Symbol.IDENTIFIER, Token.Symbol.LPAREN };

		return isSymbolInArray(symbol, expressionStartSymbols) || isPrefixOperator(symbol);
	}

	/**
	 * Checks if the given symbol is in the given array.
	 */
	private boolean isSymbolInArray(Token.Symbol symbol, Token.Symbol[] array) {
		for (Token.Symbol item : array) {
			if (symbol == item) {
				return true;
			}
		}
		return false;
	}


	// --- ZAGON ---

	/**
	 * Zagon sintaksnega analizatorja kot samostojnega programa.
	 * 
	 * @param cmdLineArgs Argumenti v ukazni vrstici.
	 */
	public static void main(final String[] cmdLineArgs) {
		System.out.println("This is PINS'24 compiler (syntax analysis):");

		try {
			if (cmdLineArgs.length == 0)
				throw new Report.Error("No source file specified in the command line.");
			if (cmdLineArgs.length > 1)
				Report.warning("Unused arguments in the command line.");

			try (SynAn synAn = new SynAn(cmdLineArgs[0])) {
				synAn.parse();
			}

			// Upajmo, da kdaj pridemo do te tocke.
			// A zavedajmo se sledecega:
			// 1. Prevod je zaradi napak v programu lahko napacen :-o
			// 2. Izvorni program se zdalec ni tisto, kar je programer hotel, da bi bil ;-)
			Report.info("Done.");
		} catch (Report.Error error) {
			// Izpis opisa napake.
			System.err.println(error.getMessage());
			System.exit(1);
		}
	}

}
