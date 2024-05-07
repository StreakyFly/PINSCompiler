package pins24.phase;

import pins24.common.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Sintaksni analizator.
 */
public class SynAn implements AutoCloseable {

	/** Leksikalni analizator. */
	private final LexAn lexAn;
	private HashMap<AST.Node, Report.Locatable> attrLoc;

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
	public AST.Node parse(HashMap<AST.Node, Report.Locatable> attrLoc) {
		this.attrLoc = attrLoc;
		final AST.Nodes<AST.MainDef> defs = parseProgram();
		if (lexAn.peekToken().symbol() != Token.Symbol.EOF)
			throw new Report.Error(lexAn.peekToken(), "Unexpected text '" + lexAn.peekToken().lexeme() + "...'.");
		return defs;
	}

	/**
	 * Opravi sintaksno analizo celega programa.
	 * program -> definition program2
	 */
	private AST.Nodes<AST.MainDef> parseProgram() {
		List<AST.MainDef> defs = new ArrayList<>();

		defs.add(parseDefinition());
        defs.addAll(parseProgram2().getAll());

        return new AST.Nodes<>(defs);
	}

	/**
	 * Continues parsing definitions if they exist.
	 * program2 -> definition program2 .
	 * program2 -> .
	 */
	private AST.Nodes<AST.MainDef> parseProgram2() {
		List<AST.MainDef> defs = new ArrayList<>();

		while (lexAn.peekToken().symbol() == Token.Symbol.VAR || lexAn.peekToken().symbol() == Token.Symbol.FUN) {
			defs.add(parseDefinition());
		}

		return new AST.Nodes<>(defs);
	}

	/**
	 * Parses a definition, either a variable or a function declaration.
	 * definition -> VAR ID ASSIGN initializers .
	 * definition -> FUN ID LP parameters RP funStatements .
	 */
	private AST.MainDef parseDefinition() {
		switch (lexAn.peekToken().symbol()) {
			case VAR:
				Token varToken = check(Token.Symbol.VAR);
				String varName = check(Token.Symbol.IDENTIFIER).lexeme();
				Token assignToken = check(Token.Symbol.ASSIGN);
				AST.Nodes<AST.Init> initializers = parseInitializers();
				AST.MainDef varDef = new AST.VarDef(varName, initializers.getAll());

				if (initializers.getAll().isEmpty()) {  // if empty, add a default initializer, an INTCONST 0
					initializers = createDefaultInitializer();
					varDef = new AST.VarDef(varName, initializers.getAll());
					attrLoc.put(varDef, new Report.Location(varToken, assignToken));
				} else {
					attrLoc.put(varDef, new Report.Location(varToken, attrLoc.get(initializers.getAll().getLast())));
				}
				return varDef;

			case FUN:
				Token funToken = check(Token.Symbol.FUN);
				Token idToken = check(Token.Symbol.IDENTIFIER);
				check(Token.Symbol.LPAREN);
				AST.Nodes<AST.ParDef> parameters = parseParameters();
				Token rpToken = check(Token.Symbol.RPAREN);
				AST.Nodes<AST.Stmt> body = parseFunStatements();
				AST.MainDef funDef = new AST.FunDef(idToken.lexeme(), parameters.getAll(), body.getAll());
				attrLoc.put(funDef, new Report.Location(funToken, body.getAll().isEmpty() ? rpToken : attrLoc.get(body.getAll().getLast())));
				return funDef;

			default:
				throw new Report.Error(lexAn.peekToken(), "A definition expected (VAR or FUN).");
		}
	}

	/**
	 * Creates a default initializer for a variable definition - an INTCONST 0.
	 */
	private AST.Nodes<AST.Init> createDefaultInitializer() {
		AST.AtomExpr defaultValue = new AST.AtomExpr(AST.AtomExpr.Type.INTCONST, "0");
		AST.Init defaultInit = new AST.Init(new AST.AtomExpr(AST.AtomExpr.Type.INTCONST, "1"), defaultValue);
		attrLoc.put(defaultValue, new Report.Location(0, 0, 0, 0));
		attrLoc.put(defaultInit, new Report.Location(0, 0, 0, 0));
		return new AST.Nodes<>(List.of(defaultInit));
	}

	/**
	 * Parses function body statements if they exist.
	 * funStatements -> .
	 * funStatements -> ASSIGN statements .
	 */
	private AST.Nodes<AST.Stmt> parseFunStatements() {
		AST.Nodes<AST.Stmt> statements = new AST.Nodes<>(new ArrayList<>());

		if (lexAn.peekToken().symbol() == Token.Symbol.ASSIGN) {
			check(Token.Symbol.ASSIGN);
			statements = parseStatements();
		}

		return statements;
	}

	/**
	 * Parses variable initializers.
	 * initializers -> .
	 * initializers -> initializer initializers2 .
	 */
	private AST.Nodes<AST.Init> parseInitializers() {
		List<AST.Init> inits = new ArrayList<>();

		if (isConstant(lexAn.peekToken().symbol())) {
			inits.add(parseInitializer());
			parseInitializers2(inits);
		}

		return new AST.Nodes<>(inits);
	}

	/**
	 * Continues parsing variable initializers if they exist.
	 * initializers2 -> .
	 * initializers2 -> COMMA initializer initializers2 .
	 */
	private void parseInitializers2(List<AST.Init> inits) {
		while (lexAn.peekToken().symbol() == Token.Symbol.COMMA) {
			check(Token.Symbol.COMMA);
			inits.add(parseInitializer());
		}
	}

	/**
	 * Parses a variable initializer.
	 * initializer -> INTCONST initializer2 .
	 * initializer -> CHARCONST .
	 * initializer -> STRINGCONST .
	 */
	private AST.Init parseInitializer() {
		AST.AtomExpr value;
		Token token = check(lexAn.peekToken().symbol());

		switch (token.symbol()) {
			case INTCONST:
				value = new AST.AtomExpr(AST.AtomExpr.Type.INTCONST, token.lexeme());
				attrLoc.put(value, token);
				AST.Init result = parseInitializer2(value);
				if (result != null) {
					return result;
				}
				break;
			case CHARCONST:
				value = new AST.AtomExpr(AST.AtomExpr.Type.CHRCONST, token.lexeme());
				attrLoc.put(value, token);
				break;
			case STRINGCONST:
				value = new AST.AtomExpr(AST.AtomExpr.Type.STRCONST, token.lexeme());
				attrLoc.put(value, token);
				break;
			default:
				throw new Report.Error(token, "A constant expected (INTCONST, CHARCONST, or STRINGCONST).");
		}

		AST.Init initNode = new AST.Init(new AST.AtomExpr(AST.AtomExpr.Type.INTCONST, "1"), value);
		attrLoc.put(initNode, token);
		return initNode;
	}

	/**
	 * Parse the second part of a variable initializer if it exists. Example: 'var x = 3 * "choo"'
	 * initializer2 -> .
	 * initializer2 -> MUL const .
	 */
	private AST.Init parseInitializer2(AST.AtomExpr possible_number) {
		if (lexAn.peekToken().symbol() == Token.Symbol.MUL) {
			check(Token.Symbol.MUL);
			AST.AtomExpr value = (AST.AtomExpr) parseConst();
			AST.Init init = new AST.Init(possible_number, value);
			attrLoc.put(init, new Report.Location(attrLoc.get(init.num), attrLoc.get(value)));
			return init;
		}
		return null;
	}

	/**
	 * Parses constants.
	 * const -> INTCONST .
	 * const -> CHARCONST .
	 * const -> STRINGCONST .
	 */
	private AST.Expr parseConst() {
		Token token = check(lexAn.peekToken().symbol());

		switch (token.symbol()) {
			case INTCONST:
				AST.AtomExpr intExpr = new AST.AtomExpr(AST.AtomExpr.Type.INTCONST, token.lexeme());
				attrLoc.put(intExpr, token);
				return intExpr;
			case CHARCONST:
				AST.AtomExpr charExpr = new AST.AtomExpr(AST.AtomExpr.Type.CHRCONST, token.lexeme());
				attrLoc.put(charExpr, token);
				return charExpr;
			case STRINGCONST:
				AST.AtomExpr stringExpr = new AST.AtomExpr(AST.AtomExpr.Type.STRCONST, token.lexeme());
				attrLoc.put(stringExpr, token);
				return stringExpr;
			default:
				throw new Report.Error(token, "A constant expected (INTCONST, CHARCONST, or STRINGCONST).");
		}
	}

	/**
	 * Parses parameters for a function declaration.
	 * parameters -> .
	 * parameters -> ID parameters2 .
	 */
	private AST.Nodes<AST.ParDef> parseParameters() {
		List<AST.ParDef> parameters = new ArrayList<>();

		if (lexAn.peekToken().symbol() == Token.Symbol.IDENTIFIER) {
			parameters.add(parseParameter());
			parameters.addAll(parseParameters2());
		}

		return new AST.Nodes<>(parameters);
	}

	/**
	 * Continues parsing parameters if they exist.
	 * parameters2 -> .
	 * parameters2 -> COMMA ID parameters2 .
	 */
	private List<AST.ParDef> parseParameters2() {
		List<AST.ParDef> parameters = new ArrayList<>();

		while (lexAn.peekToken().symbol() == Token.Symbol.COMMA) {
			check(Token.Symbol.COMMA);
			parameters.add(parseParameter());
		}

		return parameters;
	}

	/**
	 * Parses a single parameter.
	 */
	private AST.ParDef parseParameter() {
		Token idToken = check(Token.Symbol.IDENTIFIER);
		AST.ParDef parameter = new AST.ParDef(idToken.lexeme());
		attrLoc.put(parameter, idToken);
		return parameter;
	}

	/**
	 * Parses statements.
	 * statements -> statement statements2 .
	 */
	private AST.Nodes<AST.Stmt> parseStatements() {
		List<AST.Stmt> statements = new ArrayList<>();

		statements.add(parseStatement());
		statements.addAll(parseStatements2());

		return new AST.Nodes<>(statements);
	}

	/**
	 * Parses additional statements if they exist.
	 * statements2 -> .
	 * statements2 -> COMMA statement statements2 .
	 */
	private List<AST.Stmt> parseStatements2() {
		List<AST.Stmt> statements = new ArrayList<>();

		while (lexAn.peekToken().symbol() == Token.Symbol.COMMA) {
			check(Token.Symbol.COMMA);
			statements.add(parseStatement());
		}

		return statements;
	}

	/**
	 * Parses a statement.
	 * statement -> IF expression THEN statements statementIfElse END .
	 * statement -> WHILE expression DO statements END .
	 * statement -> LET definitions IN statements END .
	 * statement -> expression statement2 .
	 */
	private AST.Stmt parseStatement() {
		switch (lexAn.peekToken().symbol()) {
			case IF:
				return parseIfStatement();
			case WHILE:
				return parseWhileStatement();
			case LET:
				return parseLetStatement();
			default:
				if (isExpressionStart(lexAn.peekToken().symbol())) {
					AST.Expr lhsExpr = parseExpression();
                    return parseStatement2(lhsExpr);
				} else {
					throw new Report.Error(lexAn.peekToken(), "A statement expected (IF, WHILE, LET, or an expression).");
				}
		}
	}

	/**
	 * Continues parsing statements if they exist.
	 * statement2 -> .
	 * statement2 -> ASSIGN expression .
	 */
	private AST.Stmt parseStatement2(AST.Expr lhs) {
		if (lexAn.peekToken().symbol() == Token.Symbol.ASSIGN) {
			check(Token.Symbol.ASSIGN);
			AST.Expr rhsExpr = parseExpression();
			AST.AssignStmt assignStmt = new AST.AssignStmt(lhs, rhsExpr);
			attrLoc.put(assignStmt, new Report.Location(attrLoc.get(lhs), attrLoc.get(rhsExpr)));
			return assignStmt;
		}

		AST.ExprStmt exprStmt = new AST.ExprStmt(lhs);
		attrLoc.put(exprStmt, attrLoc.get(lhs));
		return exprStmt;
	}

	/**
	 * Parses if-then-(else)-end statements.
	 * statement -> IF expression THEN statements statementIfElse END .
	 */
	private AST.Stmt parseIfStatement() {
		Token ifToken = check(Token.Symbol.IF);
		AST.Expr condition;
		if (isExpressionStart(lexAn.peekToken().symbol())) {
			condition = parseExpression();
		} else {
			throw new Report.Error(lexAn.peekToken(), "An expression expected.");
		}
		check(Token.Symbol.THEN);
		AST.Nodes<AST.Stmt> thenStatements = parseStatements();
		AST.Nodes<AST.Stmt> elseStatements = parseStatementIfElse();
		Token endToken = check(Token.Symbol.END);
		AST.Stmt ifStmt = new AST.IfStmt(condition, thenStatements.getAll(), elseStatements.getAll());
		attrLoc.put(ifStmt, new Report.Location(ifToken, endToken));
		return ifStmt;
	}

	/**
	 * Parses the optional else part of an if statement.
	 * statementIfElse -> .
	 * statementIfElse -> ELSE statements .
	 */
	private AST.Nodes<AST.Stmt> parseStatementIfElse() {
		AST.Nodes<AST.Stmt> elseStatements = new AST.Nodes<>(new ArrayList<>());

		if (lexAn.peekToken().symbol() == Token.Symbol.ELSE) {
			check(Token.Symbol.ELSE);
			elseStatements = new AST.Nodes<>(parseStatements().getAll());
		}
		return elseStatements;
	}

	/**
	 * Parses while-do-end statements.
	 * statement -> WHILE expression DO statements END .
	 */
	private AST.Stmt parseWhileStatement() {
		Token whileToken = check(Token.Symbol.WHILE);
		AST.Expr condition;
		if (isExpressionStart(lexAn.peekToken().symbol())) {
			condition = parseExpression();
		} else {
			throw new Report.Error(lexAn.peekToken(), "An expression expected.");
		}
		check(Token.Symbol.DO);
		AST.Nodes<AST.Stmt> statements = parseStatements();
		Token endToken = check(Token.Symbol.END);
		AST.Stmt whileStmt = new AST.WhileStmt(condition, statements.getAll());
		attrLoc.put(whileStmt, new Report.Location(whileToken, endToken));
		return whileStmt;
	}

	/**
	 * Parses let-in-end statements for defining local scopes.
	 * statement -> LET definitions IN statements END .
	 */
	private AST.Stmt parseLetStatement() {
		Token letToken = check(Token.Symbol.LET);
		AST.Nodes<AST.MainDef> definitions = parseDefinitions();
		check(Token.Symbol.IN);
		AST.Nodes<AST.Stmt> statements = parseStatements();
		Token endToken = check(Token.Symbol.END);
		AST.Stmt letStmt = new AST.LetStmt(definitions.getAll(), statements.getAll());
		attrLoc.put(letStmt, new Report.Location(letToken, endToken));
		return letStmt;
	}

	/**
	 * Parses definitions.
	 * definitions -> definition definitions2 .
	 */
	private AST.Nodes<AST.MainDef> parseDefinitions() {
		List<AST.MainDef> defs = new ArrayList<>();

		defs.add(parseDefinition());
		defs.addAll(parseDefinitions2().getAll());

		return new AST.Nodes<>(defs);
	}

	/**
	 * Continues parsing definitions if they exist.
	 * definitions2 -> .
	 * definitions2 -> definition definitions2 .
	 */
	private AST.Nodes<AST.MainDef> parseDefinitions2() {
		List<AST.MainDef> defs = new ArrayList<>();

		while (lexAn.peekToken().symbol() == Token.Symbol.VAR || lexAn.peekToken().symbol() == Token.Symbol.FUN) {
			defs.add(parseDefinition());
		}

		return new AST.Nodes<>(defs);
	}

	/**
	 * Parses an atom (primary) expression.
	 * atomExpr -> INTCONST .
	 * atomExpr -> CHARCONST .
	 * atomExpr -> STRINGCONST .
	 * atomExpr -> ID functionCall .
	 * atomExpr -> LP expression RP .
	 */
	private AST.Expr parseAtomExpr() {
		switch (lexAn.peekToken().symbol()) {
			case INTCONST:
				Token intc = check(Token.Symbol.INTCONST);
				AST.AtomExpr atom_int = new AST.AtomExpr(AST.AtomExpr.Type.INTCONST, intc.lexeme());
				attrLoc.put(atom_int, intc);
				return atom_int;
			case CHARCONST:
				Token chrc = check(Token.Symbol.CHARCONST);
				AST.AtomExpr atom_chr = new AST.AtomExpr(AST.AtomExpr.Type.CHRCONST, chrc.lexeme());
				attrLoc.put(atom_chr, chrc);
				return atom_chr;
			case STRINGCONST:
				Token strc = check(Token.Symbol.STRINGCONST);
				AST.AtomExpr atom_str = new AST.AtomExpr(AST.AtomExpr.Type.STRCONST, strc.lexeme());
				attrLoc.put(atom_str, strc);
				return atom_str;
			case IDENTIFIER:
				Token idToken = check(Token.Symbol.IDENTIFIER);
				AST.Expr funcCallExpr = parseFunctionCall(idToken);
				if (funcCallExpr != null) {
					return funcCallExpr;
				} else {
					AST.VarExpr varExpr = new AST.VarExpr(idToken.lexeme());
					attrLoc.put(varExpr, idToken);
					return varExpr;
				}
			case LPAREN:
				Token lp = check(Token.Symbol.LPAREN);
				AST.Expr expr = parseExpression();
				Token rp = check(Token.Symbol.RPAREN);
				attrLoc.put(expr, new Report.Location(lp, rp));
				return expr;
			default:
				throw new Report.Error(lexAn.peekToken(), "An atom expression expected (INTCONST, CHARCONST, STRINGCONST, IDENTIFIER, or LPAREN).");
		}
	}

	/**
	 * Handles cases where an identifier is followed by a function call or nothing.
	 * functionCall -> .
	 * functionCall -> LP arguments RP .
	 */
	private AST.Expr parseFunctionCall(Token idToken) {
		if (lexAn.peekToken().symbol() == Token.Symbol.LPAREN) {
			check(Token.Symbol.LPAREN);
			List<AST.Expr> args = parseArguments();
			Token rpToken = check(Token.Symbol.RPAREN);

			AST.Nodes<AST.Expr> arguments = new AST.Nodes<>(args);
			AST.CallExpr callExpr = new AST.CallExpr(idToken.lexeme(), arguments.getAll());
			attrLoc.put(callExpr, new Report.Location(idToken, rpToken));
			return callExpr;
		}
		return null;
	}

	/**
	 * Parses arguments inside a function call if they exist.
	 * arguments -> .
	 * arguments -> expression arguments2 .
	 */
	private List<AST.Expr> parseArguments() {
		List<AST.Expr> args = new ArrayList<>();

		if (isExpressionStart(lexAn.peekToken().symbol())) {
			args.add(parseExpression());
			args.addAll(parseArguments2());
		}

		return args;
	}

	/**
	 * Continues parsing additional arguments if they exist.
	 * arguments2 -> .
	 * arguments2 -> COMMA expression arguments2 .
	 */
	private List<AST.Expr> parseArguments2() {
		List<AST.Expr> args = new ArrayList<>();

		while (lexAn.peekToken().symbol() == Token.Symbol.COMMA) {
			check(Token.Symbol.COMMA);
			args.add(parseExpression());
		}

		return args;
	}

	/**
	 * Parses postfix expressions.
	 * postfixExpr -> atomExpr postfixExpr2 .
	 */
	private AST.Expr parsePostfixExpr() {
		AST.Expr baseExpr = parseAtomExpr();
		return parsePostfixExpr2(baseExpr);
	}

	/**
	 * Continues parsing postfix expressions if they exist.
	 * postfixExpr2 -> .
	 * postfixExpr2 -> PTR postfixExpr2 .
	 */
	private AST.Expr parsePostfixExpr2(AST.Expr expr) {
		while (lexAn.peekToken().symbol() == Token.Symbol.PTR) {
			Token ptrToken = check(Token.Symbol.PTR);
			AST.UnExpr unExpr = new AST.UnExpr(AST.UnExpr.Oper.VALUEAT, expr);
			attrLoc.put(unExpr, new Report.Location(attrLoc.get(expr), ptrToken));
			expr = unExpr;
		}
		return expr;
	}

	/**
	 * Parses prefix expressions if they exist.
	 * prefixExpr -> postfixExpr .
	 * prefixExpr -> NOT prefixExpr .
	 * prefixExpr -> ADD prefixExpr .
	 * prefixExpr -> SUB prefixExpr .
	 * prefixExpr -> PTR prefixExpr .
	 */
	private AST.Expr parsePrefixExpr() {
		if (!isPrefixOperator(lexAn.peekToken().symbol())) {
			return parsePostfixExpr();
		}

		Token prefixToken = check(lexAn.peekToken().symbol());
		AST.Expr expr = parsePrefixExpr();
		AST.UnExpr unExpr = null;

		switch (prefixToken.symbol()) {
			case NOT:
				unExpr = new AST.UnExpr(AST.UnExpr.Oper.NOT, expr);
				break;
			case ADD:
				unExpr = new AST.UnExpr(AST.UnExpr.Oper.ADD, expr);
				break;
			case SUB:
				unExpr = new AST.UnExpr(AST.UnExpr.Oper.SUB, expr);
				break;
			case PTR:
				unExpr = new AST.UnExpr(AST.UnExpr.Oper.MEMADDR, expr);
				break;
		}
		attrLoc.put(unExpr, new Report.Location(prefixToken, attrLoc.get(expr)));
		return unExpr;
	}

	/**
	 * Parses multiplicative expressions.
	 * multExpr -> prefixExpr multExpr2 .
	 */
	private AST.Expr parseMultExpr() {
		AST.Expr leftExpr = parsePrefixExpr();
        return parseMultExpr2(leftExpr);
	}

	/**
	 * Continues parsing multiplicative expressions if they exist.
	 * multExpr2 -> .
	 * multExpr2 -> MUL prefixExpr multExpr2 .
	 * multExpr2 -> DIV prefixExpr multExpr2 .
	 * multExpr2 -> MOD prefixExpr multExpr2 .
	 */
	private AST.Expr parseMultExpr2(AST.Expr leftExpr) {
		while (lexAn.peekToken().symbol() == Token.Symbol.MUL || lexAn.peekToken().symbol() == Token.Symbol.DIV || lexAn.peekToken().symbol() == Token.Symbol.MOD) {
			Token operatorToken = check(lexAn.peekToken().symbol());
			Report.Locatable begLoc = attrLoc.get(leftExpr);
			AST.Expr rightExpr = parsePrefixExpr();

			switch (operatorToken.symbol()) {
				case MUL:
					leftExpr = new AST.BinExpr(AST.BinExpr.Oper.MUL, leftExpr, rightExpr);
					break;
				case DIV:
					leftExpr = new AST.BinExpr(AST.BinExpr.Oper.DIV, leftExpr, rightExpr);
					break;
				case MOD:
					leftExpr = new AST.BinExpr(AST.BinExpr.Oper.MOD, leftExpr, rightExpr);
					break;
			}
			attrLoc.put(leftExpr, new Report.Location(begLoc, attrLoc.get(rightExpr)));
		}
		return leftExpr;
	}

	/**
	 * Parses additive expressions.
	 * addExpr -> multExpr addExpr2 .
	 */
	private AST.Expr parseAddExpr() {
		AST.Expr multExpr = parseMultExpr();
		return parseAddExpr2(multExpr);
	}

	/**
	 * Continues parsing additive expressions if they exist.
	 * addExpr2 -> .
	 * addExpr2 -> ADD multExpr addExpr2 .
	 * addExpr2 -> SUB multExpr addExpr2 .
	 */
	private AST.Expr parseAddExpr2(AST.Expr leftExpr) {
		while (lexAn.peekToken().symbol() == Token.Symbol.ADD || lexAn.peekToken().symbol() == Token.Symbol.SUB) {
			Token operatorToken = check(lexAn.peekToken().symbol());
			Report.Locatable begLoc = attrLoc.get(leftExpr);
			AST.Expr rightExpr = parseMultExpr();

			switch (operatorToken.symbol()) {
				case ADD:
					leftExpr = new AST.BinExpr(AST.BinExpr.Oper.ADD, leftExpr, rightExpr);
					break;
				case SUB:
					leftExpr = new AST.BinExpr(AST.BinExpr.Oper.SUB, leftExpr, rightExpr);
					break;
			}
			attrLoc.put(leftExpr, new Report.Location(begLoc, attrLoc.get(rightExpr)));
		}
		return leftExpr;
	}

	/**
	 * Parses comparison expressions.
	 * compExpr -> addExpr compExpr2 .
	 */
	private AST.Expr parseCompExpr() {
		AST.Expr addExpr = parseAddExpr();
		return parseCompExpr2(addExpr);
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
	private AST.Expr parseCompExpr2(AST.Expr leftExpr) {
		Token.Symbol symbol = lexAn.peekToken().symbol();

		if (!isComparisonOperator(symbol)) {
			return leftExpr;
		}

		check(symbol);
		AST.BinExpr binExpr = null;
		AST.Expr rightExpr = parseAddExpr();

		switch (symbol) {
			case EQU:
				binExpr = new AST.BinExpr(AST.BinExpr.Oper.EQU, leftExpr, rightExpr);
				break;
			case NEQ:
				binExpr = new AST.BinExpr(AST.BinExpr.Oper.NEQ, leftExpr, rightExpr);
				break;
			case LTH:
				binExpr = new AST.BinExpr(AST.BinExpr.Oper.LTH, leftExpr, rightExpr);
				break;
			case GTH:
				binExpr = new AST.BinExpr(AST.BinExpr.Oper.GTH, leftExpr, rightExpr);
				break;
			case LEQ:
				binExpr = new AST.BinExpr(AST.BinExpr.Oper.LEQ, leftExpr, rightExpr);
				break;
			case GEQ:
				binExpr = new AST.BinExpr(AST.BinExpr.Oper.GEQ, leftExpr, rightExpr);
				break;
		}

		if (isComparisonOperator(lexAn.peekToken().symbol())) {
			throw new Report.Error(lexAn.peekToken(), "Chained comparisons without parentheses are not allowed.");
		}

		attrLoc.put(binExpr, new Report.Location(attrLoc.get(leftExpr), attrLoc.get(rightExpr)));
		return binExpr;
	}

	/**
	 * Parses logical AND expressions.
	 * andExpr -> compExpr andExpr2 .
	 */
	private AST.Expr parseAndExpr() {
		AST.Expr compExpr = parseCompExpr();
		return parseAndExpr2(compExpr);
	}

	/**
	 * Continues parsing logical AND expressions if they exist.
	 * andExpr2 -> .
	 * andExpr2 -> AND compExpr andExpr2 .
	 */
	private AST.Expr parseAndExpr2(AST.Expr leftExpr) {
		while (lexAn.peekToken().symbol() == Token.Symbol.AND) {
			check(Token.Symbol.AND);
			AST.Expr rightExpr = parseCompExpr();
			Report.Locatable begLoc = attrLoc.get(leftExpr);
			leftExpr = new AST.BinExpr(AST.BinExpr.Oper.AND, leftExpr, rightExpr);
			attrLoc.put(leftExpr, new Report.Location(begLoc, attrLoc.get(rightExpr)));
		}
		return leftExpr;
	}

	/**
	 * Parses logical OR expressions.
	 * orExpr -> andExpr orExpr2 .
	 */
	private AST.Expr parseOrExpr() {
		AST.Expr andExpr = parseAndExpr();
		return parseOrExpr2(andExpr);
	}

	/**
	 * Continues parsing logical OR expressions if they exist.
	 * orExpr2 -> .
	 * orExpr2 -> OR andExpr orExpr2 .
	 */
	private AST.Expr parseOrExpr2(AST.Expr leftExpr) {
		while (lexAn.peekToken().symbol() == Token.Symbol.OR) {
			check(Token.Symbol.OR);
			AST.Expr rightExpr = parseAndExpr();
			Report.Locatable begLoc = attrLoc.get(leftExpr);
			leftExpr = new AST.BinExpr(AST.BinExpr.Oper.OR, leftExpr, rightExpr);
			attrLoc.put(leftExpr, new Report.Location(begLoc, attrLoc.get(rightExpr)));
		}
		return leftExpr;
	}

	/**
	 * Calls the top-level function to parse complete expressions.
	 * expression -> orExpr .
	 */
	private AST.Expr parseExpression() {
        return parseOrExpr();
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
				System.out.println("SynAn.java has been updated and now requires a parameter. Run Abstr.java instead.");
//				synAn.parse();  // TODO - WARNING: commented, because SynAn.java has been updated and now requires a parameter. Run Abstr.java instead.
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
