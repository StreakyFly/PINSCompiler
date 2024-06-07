package pins24.phase;

import java.util.*;
import pins24.common.*;

/**
 * Generiranje kode.
 */
public class CodeGen {

	@SuppressWarnings({ "doclint:missing" })
	public CodeGen() {
		throw new Report.InternalError();
	}

	/**
	 * Abstraktno sintaksno drevo z dodanimi atributi izracuna pomnilniske
	 * predstavitve.
	 *
	 * Atributi:
	 * <ol>
	 * <li>({@link Abstr}) lokacija kode, ki pripada posameznemu vozliscu;</li>
	 * <li>({@link SemAn}) definicija uporabljenega imena;</li>
	 * <li>({@link SemAn}) ali je dani izraz levi izraz;</li>
	 * <li>({@link Memory}) klicni zapis funkcije;</li>
	 * <li>({@link Memory}) dostop do parametra;</li>
	 * <li>({@link Memory}) dostop do spremenljivke;</li>
	 * <li>({@link CodeGen}) seznam ukazov, ki predstavljajo kodo programa;</li>
	 * <li>({@link CodeGen}) seznam ukazov, ki predstavljajo podatke programa.</li>
	 * </ol>
	 */
	public static class AttrAST extends Memory.AttrAST {

		/** Atribut: seznam ukazov, ki predstavljajo kodo programa. */
		public final Map<AST.Node, List<PDM.CodeInstr>> attrCode;

		/** Atribut: seznam ukazov, ki predstavljajo podatke programa. */
		public final Map<AST.Node, List<PDM.DataInstr>> attrData;

		/**
		 * Ustvari novo abstraktno sintaksno drevo z dodanimi atributi generiranja kode.
		 *
		 * @param attrAST  Abstraktno sintaksno drevo z dodanimi atributi pomnilniske
		 *                 predstavitve.
		 * @param attrCode Attribut: seznam ukazov, ki predstavljajo kodo programa.
		 * @param attrData Attribut: seznam ukazov, ki predstavljajo podatke programa.
		 */
		public AttrAST(final Memory.AttrAST attrAST, final Map<AST.Node, List<PDM.CodeInstr>> attrCode,
					   final Map<AST.Node, List<PDM.DataInstr>> attrData) {
			super(attrAST);
			this.attrCode = attrCode;
			this.attrData = attrData;
		}

		/**
		 * Ustvari novo abstraktno sintaksno drevo z dodanimi atributi generiranja kode.
		 *
		 * @param attrAST Abstraktno sintaksno drevo z dodanimi atributi generiranja
		 *                kode.
		 */
		public AttrAST(final AttrAST attrAST) {
			super(attrAST);
			this.attrCode = attrAST.attrCode;
			this.attrData = attrAST.attrData;
		}

		@Override
		public String head(final AST.Node node, final boolean highlighted) {
			final StringBuffer head = new StringBuffer();
			head.append(super.head(node, false));
			return head.toString();
		}

		@Override
		public void desc(final int indent, final AST.Node node, final boolean highlighted) {
			super.desc(indent, node, false);
			System.out.print(highlighted ? "\033[31m" : "");
			if (attrCode.get(node) != null) {
				List<PDM.CodeInstr> instrs = attrCode.get(node);
				if (instrs != null) {
					if (indent > 0)
						System.out.printf("%" + indent + "c", ' ');
					System.out.printf("--- Code: ---\n");
					for (final PDM.CodeInstr instr : instrs) {
						if (indent > 0)
							System.out.printf("%" + indent + "c", ' ');
						System.out.println((instr instanceof PDM.LABEL ? "" : "  ") + instr.toString());
					}
				}
			}
			if (attrData.get(node) != null) {
				List<PDM.DataInstr> instrs = attrData.get(node);
				if (instrs != null) {
					if (indent > 0)
						System.out.printf("%" + indent + "c", ' ');
					System.out.printf("--- Data: ---\n");
					for (final PDM.DataInstr instr : instrs) {
						if (indent > 0)
							System.out.printf("%" + indent + "c", ' ');
						System.out.println((instr instanceof PDM.LABEL ? "" : "  ") + instr.toString());
					}
				}
			}
			System.out.print(highlighted ? "\033[30m" : "");
			return;
		}

	}

	/**
	 * Izracuna kodo programa
	 *
	 * @param memoryAttrAST Abstraktno sintaksno drevo z dodanimi atributi izracuna
	 *                      pomnilniske predstavitve.
	 * @return Abstraktno sintaksno drevo z dodanimi atributi izracuna pomnilniske
	 *         predstavitve.
	 */
	public static AttrAST generate(final Memory.AttrAST memoryAttrAST) {
		AttrAST attrAST = new AttrAST(memoryAttrAST, new HashMap<AST.Node, List<PDM.CodeInstr>>(),
				new HashMap<AST.Node, List<PDM.DataInstr>>());
		(new CodeGenerator(attrAST)).generate();
		return attrAST;
	}

	/**
	 * Generiranje kode v abstraktnem sintaksnem drevesu.
	 */
	private static class CodeGenerator {

		/**
		 * Abstraktno sintaksno drevo z dodanimi atributi izracuna pomnilniske
		 * predstavitve.
		 */
		private final AttrAST attrAST;

		/** Stevec anonimnih label. */
		private int labelCounter = 0;

		/**
		 * Ustvari nov generator kode v abstraktnem sintaksnem drevesu.
		 *
		 * @param attrAST Abstraktno sintaksno drevo z dodanimi atributi izracuna
		 *                pomnilniske predstavitve.
		 */
		public CodeGenerator(final AttrAST attrAST) {
			this.attrAST = attrAST;
		}

		/**
		 * Sprozi generiranje kode v abstraktnem sintaksnem drevesu.
		 *
		 * @return Abstraktno sintaksno drevo z dodanimi atributi izracuna pomnilniske
		 *         predstavitve.
		 */
		public AttrAST generate() {
			attrAST.ast.accept(new Generator(), null);
			return new AttrAST(attrAST, Collections.unmodifiableMap(attrAST.attrCode),
					Collections.unmodifiableMap(attrAST.attrData));
		}

		/** Obiskovalec, ki generira kodo v abstraktnem sintaksnem drevesu. */
		private class Generator implements AST.FullVisitor<List<PDM.CodeInstr>, Mem.Frame> {

			private final Map<String, Integer> funNameCount = new HashMap<>();  // to track count of functions with the same name

			@SuppressWarnings({"doclint:missing"})
			public Generator() {
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.FunDef funDef, final Mem.Frame frame) {
				if (funDef.stmts.getAll().isEmpty()) {  // return if function has no body
					return new LinkedList<>();
				}

				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(funDef);
				Mem.Frame funFrame = attrAST.attrFrame.get(funDef);

				String labelName = funFrame.name;
				if (funNameCount.containsKey(labelName)) {
					int count = funNameCount.get(labelName) + 1;
					funNameCount.put(labelName, count);
					labelName += ":" + count;
				} else {
					funNameCount.put(labelName, 0);
				}

				code.add(new PDM.LABEL(labelName, loc));  // label for function

				int varsSize = funFrame.varsSize - 8;  // -8 because varsSize contains the size of FP and RA (4 bytes each)
				// initialize the memory space for all the local variables (accepts a negative operand)
				code.add(new PDM.PUSH(-varsSize, loc));
				code.add(new PDM.POPN(loc));

//				for (AST.ParDef par : funDef.pars) {  // parameters don't need to be handled here
//					code.addAll(par.accept(this, frame));
//				}

				for (AST.Stmt stmt : funDef.stmts) {
					List<PDM.CodeInstr> stmtCode = stmt.accept(this, funFrame);
					List<PDM.CodeInstr> cleanedCode = new LinkedList<>(stmtCode);
					if (cleanedCode.size() > 1 && cleanedCode.getLast() instanceof PDM.POPN) {
						// I'm not sure if this If statement is ok; when to remove the last two commands?
						cleanedCode.removeLast();  // get rid of unnecessary code (POPN)
						cleanedCode.removeLast();  // get rid of unnecessary code (PUSH)
					}
					code.addAll(cleanedCode);
				}
//				code.addAll(funDef.stmts.accept(this, funFrame));

				int parsSize = funFrame.parsSize - 4;  // -4 because parsSize contains the size of SL (4 bytes)
				code.add(new PDM.PUSH(parsSize, loc));
				code.add(new PDM.RETN(funFrame, loc));  // return from function

				attrAST.attrCode.put(funDef, code);

//				// Don't return this function's code instructions to parent,
//				// as that may duplicate function definition instructions in case of nested functions.
//				// See `CodeSegmentGenerator.Generator.visit(AST.FunDef)`.
				return new LinkedList<>();
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.VarDef varDef, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				List<PDM.DataInstr> data = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(varDef);
				Mem.Access access = attrAST.attrVarAccess.get(varDef);

				String label = ":" + labelCounter++;
				switch (access) {
					case Mem.AbsAccess absAccess -> {  // global variable
						code.add(new PDM.NAME(absAccess.name, loc));
						code.add(new PDM.NAME(label, loc));
						code.add(new PDM.INIT(loc));
						data.add(new PDM.LABEL(absAccess.name, loc));
						data.add(new PDM.SIZE(absAccess.size, loc));
					}
					case Mem.RelAccess relAccess -> {  // local variable
						code.add(new PDM.REGN(PDM.REGN.Reg.FP, loc));
						code.add(new PDM.PUSH(relAccess.offset, loc));
						code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));
						code.add(new PDM.NAME(label, loc));
						code.add(new PDM.INIT(loc));
					}
					default -> throw new Report.Error("RelAccess or AbsAccess expected in VarDef visit method");
				}

				data.add(new PDM.LABEL(label, loc));
				if (access.inits != null && !access.inits.isEmpty()) {
					for (Integer initValue : access.inits) {
						data.add(new PDM.DATA(initValue, loc));
					}
				}

				attrAST.attrCode.put(varDef, code);
				attrAST.attrData.put(varDef, data);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.VarExpr varExpr, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(varExpr);
				AST.Def def = attrAST.attrDef.get(varExpr);

				Mem.Access access;
				switch (def) {
					case final AST.VarDef varDef: {
						access = attrAST.attrVarAccess.get(varDef);
						break;
					}
					case final AST.ParDef parDef: {
						access = attrAST.attrParAccess.get(parDef);
						break;
					}
					default:
						throw new Report.Error("VarDef or ParDef expected in VarExpr visit method");
				}

				switch (access) {
					case final Mem.RelAccess relAccess: {
						code.add(new PDM.REGN(PDM.REGN.Reg.FP, loc));  // start with current FP
						// adjust FP to the FP of the function where the variable is defined
						int depthDiff = frame.depth - relAccess.depth;
						for (int i = 0; i < depthDiff; i++) {
							code.add(new PDM.LOAD(loc));  // load static link from FP
						}
						code.add(new PDM.PUSH(relAccess.offset, loc));
						code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));
						break;
					}
					case final Mem.AbsAccess absAccess: {
						code.add(new PDM.NAME(absAccess.name, loc));
						break;
					}
					default:
						throw new Report.Error("RelAccess or AbsAccess expected in VarExpr visit method");
				}

				code.add(new PDM.LOAD(loc));  // load the value of the variable

				attrAST.attrCode.put(varExpr, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(AST.LetStmt letStmt, Mem.Frame frame) {
				List<PDM.CodeInstr> code = new ArrayList<>();

				code.addAll(letStmt.defs.accept(this, frame));
				code.addAll(letStmt.stmts.accept(this, frame));

				attrAST.attrCode.put(letStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.ExprStmt exprStmt, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(exprStmt);

				code.addAll(exprStmt.expr.accept(this, frame));

				code.add(new PDM.PUSH(4, loc));
				code.add(new PDM.POPN(loc));

				attrAST.attrCode.put(exprStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.AssignStmt assignStmt, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(assignStmt);

				List<PDM.CodeInstr> srcExprCode = assignStmt.srcExpr.accept(this, frame);  // right side of the assignment
				code.addAll(srcExprCode);
				List<PDM.CodeInstr> dstExprCode = assignStmt.dstExpr.accept(this, frame);  // left side of the assignment
				dstExprCode = removeLastLoadCodeInstr(dstExprCode);  // removes the unnecessary LOAD command - we don't need to load the value of the variable we're assigning to
				code.addAll(dstExprCode);

				code.add(new PDM.SAVE(loc));

				attrAST.attrCode.put(assignStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.CallExpr callExpr, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(callExpr);
				AST.FunDef def = (AST.FunDef) attrAST.attrDef.get(callExpr);
				Mem.Frame callingFunFrame = attrAST.attrFrame.get(def);  // get the Frame of the function that's being called

//				for (AST.Expr arg : callExpr.args) {
//					code.addAll(arg.accept(this, frame));
//				}
				// semantic rules state that function arguments are evaluated from right to left
				for (int i = callExpr.args.size() - 1; i >= 0; i--) {
					code.addAll(callExpr.args.get(i).accept(this, frame));
				}

				// calling function is declared in the same or outer scopes of the caller function
				if (callingFunFrame.depth - 1 <= frame.depth) {
					int depthDiff = frame.depth - callingFunFrame.depth;
					code.add(new PDM.REGN(PDM.REGN.Reg.FP, loc));
					// calling function is at the same depth as the current function
					if (depthDiff == 0) {
						code.add(new PDM.LOAD(loc));
					} else {
						// get the FP of the caller
						for (int i = 0; i < depthDiff; i++) {
							code.add(new PDM.PUSH(-4, loc));
							code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));
							code.add(new PDM.LOAD(loc));
						}
					}
				}
				// calling function is declared in inner scopes of the caller function - not visible.
				else {
					throw new Report.Error("Function " + def.name + " is not visible in the current scope.");
				}

				String labelName = getFullFunName(callingFunFrame.name);
				code.add(new PDM.NAME(labelName, loc));  // push the full name of the function, eg. 'main.f1:1', not just 'f1' or 'main.f1'
				code.add(new PDM.CALL(callingFunFrame, loc));

				attrAST.attrCode.put(callExpr, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.AtomExpr atomExpr, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				List<PDM.DataInstr> data = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(atomExpr);

				switch (atomExpr.type) {
					case INTCONST -> code.add(new PDM.PUSH(Memory.decodeIntConst(atomExpr, loc), loc));
					case CHRCONST -> code.add(new PDM.PUSH(Memory.decodeChrConst(atomExpr, loc), loc));
					case STRCONST -> {
						String strConstLabel = ":" + labelCounter++;
						code.add(new PDM.NAME(strConstLabel, loc));
						data.add(new PDM.LABEL(strConstLabel, loc));
						Vector<Integer> values = Memory.decodeStrConst(atomExpr, loc);
						for (Integer value : values) {
							data.add(new PDM.DATA(value, loc));
						}
						attrAST.attrData.put(atomExpr, data);
					}
				}

				attrAST.attrCode.put(atomExpr, code);
				return code;
			}

//			@Override
//			public List<PDM.CodeInstr> visit(final AST.BinExpr binExpr, final Mem.Frame frame) {
//				// TODO WARNING! This method DOES follow semantic rules which state that the right operand should be evaluated first and then the left one.
//				//  although I think it should be the other way around
//				List<PDM.CodeInstr> code = new LinkedList<>();
//				Report.Locatable loc = attrAST.attrLoc.get(binExpr);
//
//				// semantic rules state that the right operand should be evaluated first and then the left one
//				code.addAll(binExpr.sndExpr.accept(this, frame));
//				code.addAll(binExpr.fstExpr.accept(this, frame));
//
//				// pretty sure this does the same thing as using addCodeInstrToSwapBinOperands() method
////				if (binExpr.oper == AST.BinExpr.Oper.DIV || binExpr.oper == AST.BinExpr.Oper.SUB || binExpr.oper == AST.BinExpr.Oper.MOD) {
////					code.addAll(binExpr.fstExpr.accept(this, frame));
////					code.addAll(binExpr.sndExpr.accept(this, frame));
////				} else {
////					code.addAll(binExpr.sndExpr.accept(this, frame));
////					code.addAll(binExpr.fstExpr.accept(this, frame));
////				}
//
//				switch (binExpr.oper) {
//					case OR -> code.add(new PDM.OPER(PDM.OPER.Oper.OR, loc));
//					case AND -> code.add(new PDM.OPER(PDM.OPER.Oper.AND, loc));
//					case EQU -> code.add(new PDM.OPER(PDM.OPER.Oper.EQU, loc));
//					case GEQ -> code.add(new PDM.OPER(PDM.OPER.Oper.LEQ, loc));
//					case LEQ -> code.add(new PDM.OPER(PDM.OPER.Oper.GEQ, loc));
//					case GTH -> code.add(new PDM.OPER(PDM.OPER.Oper.LTH, loc));
//					case LTH -> code.add(new PDM.OPER(PDM.OPER.Oper.GTH, loc));
//					case ADD -> code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));
//					case MUL -> code.add(new PDM.OPER(PDM.OPER.Oper.MUL, loc));
//					case NEQ -> code.add(new PDM.OPER(PDM.OPER.Oper.NEQ, loc));
//					case DIV -> {
//						code.addAll(addCodeInstrToSwapBinOperands(loc));
//						code.add(new PDM.OPER(PDM.OPER.Oper.DIV, loc));
//					}
//					case SUB -> {
//						code.addAll(addCodeInstrToSwapBinOperands(loc));
//						code.add(new PDM.OPER(PDM.OPER.Oper.SUB, loc));
//					}
//					case MOD -> {
//						code.addAll(addCodeInstrToSwapBinOperands(loc));
//						code.add(new PDM.OPER(PDM.OPER.Oper.MOD, loc));
//					}
//				}
//
//				attrAST.attrCode.put(binExpr, code);
//				return code;
//			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.BinExpr binExpr, final Mem.Frame frame) {
				// TODO WARNING! This method doesn't follow semantic rules which state that the right operand should be evaluated first and then the left one.
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(binExpr);

				code.addAll(binExpr.fstExpr.accept(this, frame));
				code.addAll(binExpr.sndExpr.accept(this, frame));

				switch (binExpr.oper) {
					case OR -> code.add(new PDM.OPER(PDM.OPER.Oper.OR, loc));
					case AND -> code.add(new PDM.OPER(PDM.OPER.Oper.AND, loc));
					case EQU -> code.add(new PDM.OPER(PDM.OPER.Oper.EQU, loc));
					case GEQ -> code.add(new PDM.OPER(PDM.OPER.Oper.GEQ, loc));
					case LEQ -> code.add(new PDM.OPER(PDM.OPER.Oper.LEQ, loc));
					case GTH -> code.add(new PDM.OPER(PDM.OPER.Oper.GTH, loc));
					case LTH -> code.add(new PDM.OPER(PDM.OPER.Oper.LTH, loc));
					case ADD -> code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));
					case MUL -> code.add(new PDM.OPER(PDM.OPER.Oper.MUL, loc));
					case NEQ -> code.add(new PDM.OPER(PDM.OPER.Oper.NEQ, loc));
					case DIV -> code.add(new PDM.OPER(PDM.OPER.Oper.DIV, loc));
					case SUB -> code.add(new PDM.OPER(PDM.OPER.Oper.SUB, loc));
					case MOD -> code.add(new PDM.OPER(PDM.OPER.Oper.MOD, loc));
				}

				attrAST.attrCode.put(binExpr, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.UnExpr unExpr, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(unExpr);

				List<PDM.CodeInstr> exprCode = unExpr.expr.accept(this, frame);

				if (unExpr.oper == AST.UnExpr.Oper.MEMADDR) {
					exprCode = removeLastLoadCodeInstr(exprCode);
				}

				code.addAll(exprCode);

				switch (unExpr.oper) {
					case NOT -> code.add(new PDM.OPER(PDM.OPER.Oper.NOT, loc));
					case ADD -> {}  // do nothing
					case SUB -> code.add(new PDM.OPER(PDM.OPER.Oper.NEG, loc));  // negate the value
					case MEMADDR -> {}  // remove the last LOAD command (handled above)
					case VALUEAT -> code.add(new PDM.LOAD(loc));  // load the value from the address
				}

				attrAST.attrCode.put(unExpr, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.IfStmt ifStmt, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(ifStmt);

				int counter = labelCounter++;
				String thenLabel = "then:" + counter;
				String elseLabel = "else:" + counter;
				String endifLabel = "endif:" + counter;

				// prepare for a conditional jump
				code.addAll(ifStmt.cond.accept(this, frame));
				code.add(new PDM.NAME(thenLabel, loc));
				code.add(new PDM.NAME(elseLabel, loc));
				code.add(new PDM.CJMP(loc));  // if false, jump to the else part, otherwise the then part

				// execute the then part if the condition was true
				code.add(new PDM.LABEL(thenLabel, loc));
				code.addAll(ifStmt.thenStmts.accept(this, frame));
				code.add(new PDM.NAME(endifLabel, loc));
				code.add(new PDM.UJMP(loc));  // jump to the end of the if statement

				// execute the else part if the condition was false
				code.add(new PDM.LABEL(elseLabel, loc));
				code.addAll(ifStmt.elseStmts.accept(this, frame));
				code.add(new PDM.NAME(endifLabel, loc));
				code.add(new PDM.UJMP(loc));  // jump to the end of the if statement

				// label marking the end of the if statement
				code.add(new PDM.LABEL(endifLabel, loc));

				attrAST.attrCode.put(ifStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.WhileStmt whileStmt, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(whileStmt);

				int counter = labelCounter++;
				String condLabel = "while:" + counter;
				String doLabel = "dowhile:" + counter;
				String endLabel = "endwhile:" + counter;

				// label for the start of the loop to jump back to
				code.add(new PDM.LABEL(condLabel, loc));

				// prepare for a conditional jump
				code.addAll(whileStmt.cond.accept(this, frame));  // code for the condition
				code.add(new PDM.NAME(doLabel, loc));
				code.add(new PDM.NAME(endLabel, loc));
				code.add(new PDM.CJMP(loc));  // if false, jump to the end, otherwise execute the loop body

				// prepare for the loop body
				code.add(new PDM.LABEL(doLabel, loc));  // label marking the start of the loop body
				code.addAll(whileStmt.stmts.accept(this, frame));  // code for the loop body
				// unconditional jump back to the start of the loop
				code.add(new PDM.NAME(condLabel, loc));
				code.add(new PDM.UJMP(loc));

				// label marking the end of the loop
				code.add(new PDM.LABEL(endLabel, loc));

				attrAST.attrCode.put(whileStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.Nodes<? extends AST.Node> nodes, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				for (final AST.Node node : nodes) {
					code.addAll(node.accept(this, frame));
				}
				return code;
			}

			private String getFullFunName(String funName) {
				String fullFunName = funName;
				if (funNameCount.containsKey(funName) && funNameCount.get(funName) > 0) {
					fullFunName += ":" + funNameCount.get(funName);
				}
				return fullFunName;
			}

			private List<PDM.CodeInstr> removeLastLoadCodeInstr(List<PDM.CodeInstr> code) {
				if (code.size() > 1 && code.getLast() instanceof PDM.LOAD) {
					List<PDM.CodeInstr> newCode = new LinkedList<>(code);
					newCode.removeLast();
					return newCode;
				} else {
					throw new Report.Error("Attempted to remove the last LOAD command, but the last command is not a LOAD command");
				}
			}

			private List<PDM.CodeInstr> addCodeInstrToSwapBinOperands(Report.Locatable loc) {
				List<PDM.CodeInstr> code = new ArrayList<>();

				// A B A_address
				code.add(new PDM.REGN(PDM.REGN.Reg.SP, loc));
				code.add(new PDM.PUSH(4, loc));
				code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));

				// A B A
				code.add(new PDM.LOAD(loc));

				// A B A B_address
				code.add(new PDM.REGN(PDM.REGN.Reg.SP, loc));
				code.add(new PDM.PUSH(4, loc));
				code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));

				// A B A B
				code.add(new PDM.LOAD(loc));

				// A B A B A1_address
				code.add(new PDM.REGN(PDM.REGN.Reg.SP, loc));
				code.add(new PDM.PUSH(12, loc));
				code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));

				// B B A
				code.add(new PDM.SAVE(loc));

				// B B A B2_address
				code.add(new PDM.REGN(PDM.REGN.Reg.SP, loc));
				code.add(new PDM.PUSH(4, loc));
				code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));

				// B A
				code.add(new PDM.SAVE(loc));

				return code;
			}


		}



	}

	/**
	 * Generator seznama ukazov, ki predstavljajo kodo programa.
	 */
	public static class CodeSegmentGenerator {

		/**
		 * Abstraktno sintaksno drevo z dodanimi atributi izracuna pomnilniske
		 * predstavitve.
		 */
		private final AttrAST attrAST;

		/** Seznam ukazov za inicializacijo staticnih spremenljivk. */
		private final Vector<PDM.CodeInstr> codeInitSegment = new Vector<PDM.CodeInstr>();

		/** Seznam ukazov funkcij. */
		private final Vector<PDM.CodeInstr> codeFunsSegment = new Vector<PDM.CodeInstr>();

		/** Klicni zapis funkcije {@code main}. */
		private Mem.Frame main = null;

		/**
		 * Ustvari nov generator seznama ukazov, ki predstavljajo kodo programa.
		 *
		 * @param attrAST Abstraktno sintaksno drevo z dodanimi atributi izracuna
		 *                pomnilniske predstavitve.
		 */
		public CodeSegmentGenerator(final AttrAST attrAST) {
			this.attrAST = attrAST;
		}

		/**
		 * Izracuna seznam ukazov, ki predstavljajo kodo programa.
		 *
		 * @return Seznam ukazov, ki predstavljajo kodo programa.
		 */
		public List<PDM.CodeInstr> codeSegment() {
			attrAST.ast.accept(new Generator(), null);
			codeInitSegment.addLast(new PDM.PUSH(0, null));
			codeInitSegment.addLast(new PDM.NAME("main", null));
			codeInitSegment.addLast(new PDM.CALL(main, null));
			codeInitSegment.addLast(new PDM.PUSH(0, null));
			codeInitSegment.addLast(new PDM.NAME("exit", null));
			codeInitSegment.addLast(new PDM.CALL(null, null));
			final Vector<PDM.CodeInstr> codeSegment = new Vector<PDM.CodeInstr>();
			codeSegment.addAll(codeInitSegment);
			codeSegment.addAll(codeFunsSegment);
			return Collections.unmodifiableList(codeSegment);
		}

		/**
		 * Obiskovalec, ki izracuna seznam ukazov, ki predstavljajo kodo programa.
		 */
		private class Generator implements AST.FullVisitor<Object, Object> {

			@SuppressWarnings({ "doclint:missing" })
			public Generator() {
			}

			@Override
			public Object visit(final AST.FunDef funDef, final Object arg) {
				if (funDef.stmts.size() == 0)
					return null;
				List<PDM.CodeInstr> code = attrAST.attrCode.get(funDef);
				codeFunsSegment.addAll(code);
				funDef.pars.accept(this, arg);
				funDef.stmts.accept(this, arg);
				switch (funDef.name) {
					case "main" -> main = attrAST.attrFrame.get(funDef);
				}
				return null;
			}

			@Override
			public Object visit(final AST.VarDef varDef, final Object arg) {
				switch (attrAST.attrVarAccess.get(varDef)) {
					case Mem.AbsAccess __: {
						List<PDM.CodeInstr> code = attrAST.attrCode.get(varDef);
						codeInitSegment.addAll(code);
						break;
					}
					case Mem.RelAccess __: {
						break;
					}
					default:
						throw new Report.InternalError();
				}
				return null;
			}

		}

	}

	/**
	 * Generator seznama ukazov, ki predstavljajo podatke programa.
	 */
	public static class DataSegmentGenerator {

		/**
		 * Abstraktno sintaksno drevo z dodanimi atributi izracuna pomnilniske
		 * predstavitve.
		 */
		private final AttrAST attrAST;

		/** Seznam ukazov, ki predstavljajo podatke programa. */
		private final Vector<PDM.DataInstr> dataSegment = new Vector<PDM.DataInstr>();

		/**
		 * Ustvari nov generator seznama ukazov, ki predstavljajo podatke programa.
		 *
		 * @param attrAST Abstraktno sintaksno drevo z dodanimi atributi izracuna
		 *                pomnilniske predstavitve.
		 */
		public DataSegmentGenerator(final AttrAST attrAST) {
			this.attrAST = attrAST;
		}

		/**
		 * Izracuna seznam ukazov, ki predstavljajo podatke programa.
		 *
		 * @return Seznam ukazov, ki predstavljajo podatke programa.
		 */
		public List<PDM.DataInstr> dataSegment() {
			attrAST.ast.accept(new Generator(), null);
			return Collections.unmodifiableList(dataSegment);
		}

		/**
		 * Obiskovalec, ki izracuna seznam ukazov, ki predstavljajo podatke programa.
		 */
		private class Generator implements AST.FullVisitor<Object, Object> {

			@SuppressWarnings({ "doclint:missing" })
			public Generator() {
			}

			@Override
			public Object visit(final AST.VarDef varDef, final Object arg) {
				List<PDM.DataInstr> data = attrAST.attrData.get(varDef);
				if (data != null)
					dataSegment.addAll(data);
				varDef.inits.accept(this, arg);
				return null;
			}

			@Override
			public Object visit(final AST.AtomExpr atomExpr, final Object arg) {
				List<PDM.DataInstr> data = attrAST.attrData.get(atomExpr);
				if (data != null)
					dataSegment.addAll(data);
				return null;
			}

		}

	}

	// --- ZAGON ---

	/**
	 * Zagon izracuna pomnilniske predstavitve kot samostojnega programa.
	 *
	 * @param cmdLineArgs Argumenti v ukazni vrstici.
	 */
	public static void main(final String[] cmdLineArgs) {
		System.out.println("This is PINS'24 compiler (code generation):");

		try {
			if (cmdLineArgs.length == 0)
				throw new Report.Error("No source file specified in the command line.");
			if (cmdLineArgs.length > 1)
				Report.warning("Unused arguments in the command line.");

			try (SynAn synAn = new SynAn(cmdLineArgs[0])) {
				// abstraktna sintaksa:
				final Abstr.AttrAST abstrAttrAST = Abstr.constructAST(synAn);
				// semanticna analiza:
				final SemAn.AttrAST semanAttrAST = SemAn.analyze(abstrAttrAST);
				// pomnilniska predstavitev:
				final Memory.AttrAST memoryAttrAST = Memory.organize(semanAttrAST);
				// generiranje kode:
				final CodeGen.AttrAST codegenAttrAST = CodeGen.generate(memoryAttrAST);

				(new AST.Logger(codegenAttrAST)).log();
				{
					int addr = 0;
					final List<PDM.CodeInstr> codeSegment = (new CodeSegmentGenerator(codegenAttrAST)).codeSegment();
					{
						System.out.println("\n\033[1mCODE SEGMENT:\033[0m");
						for (final PDM.CodeInstr instr : codeSegment) {
							System.out.printf("%8d [%s] %s\n", addr, instr.size(),
									(instr instanceof PDM.LABEL ? "" : "  ") + instr.toString());
							addr += instr.size();
						}
					}
					final List<PDM.DataInstr> dataSegment = (new DataSegmentGenerator(codegenAttrAST)).dataSegment();
					{
						System.out.println("\n\033[1mDATA SEGMENT:\033[0m");
						for (final PDM.DataInstr instr : dataSegment) {
							System.out.printf("%8d [%s] %s\n", addr, (instr instanceof PDM.SIZE) ? " " : instr.size(),
									(instr instanceof PDM.LABEL ? "" : "  ") + instr.toString());
							addr += instr.size();
						}
					}
					System.out.println();
				}
			}

			// Upajmo, da kdaj pridemo to te tocke.
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
