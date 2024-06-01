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

			private Map<String, Integer> funNameCount = new HashMap<>();  // to track count of functions with the same name

			@SuppressWarnings({"doclint:missing"})
			public Generator() {
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.FunDef funDef, final Mem.Frame frame) {
				if (funDef.stmts.getAll().isEmpty()) {  // return if function has no body
					return null;
				}

				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(funDef);

				Mem.Frame funFrame = attrAST.attrFrame.get(funDef);

				String name = funFrame.name;
				if (funNameCount.containsKey(name)) {
					int count = funNameCount.get(name) + 1;
					funNameCount.put(name, count);
					name += ":" + count;
				} else {
					funNameCount.put(name, 0);
				}

				code.add(new PDM.LABEL(name, loc));  // label for function

				int varsSize = funFrame.varsSize - 8;
				code.add(new PDM.PUSH(-varsSize, loc));
				code.add(new PDM.POPN(loc));

//				for (AST.ParDef par : funDef.pars) {
//					List<PDM.CodeInstr> parCode = par.accept(this, frame);
//					System.out.println("parCode: " + parCode);
//					code.addAll(parCode);
//				}

				// generate code for function body
				for (AST.Stmt stmt : funDef.stmts) {
					List<PDM.CodeInstr> stmtCode = stmt.accept(this, funFrame);
					List<PDM.CodeInstr> cleanedCode = new LinkedList<>(stmtCode);
					if (cleanedCode.size() > 1 && cleanedCode.getLast() instanceof PDM.POPN) {
						// TODO I'm not sure if this if statement is ok, when to remove the last two commands?
						cleanedCode.removeLast();  // get rid of unnecessary code
						cleanedCode.removeLast();
					}
					code.addAll(cleanedCode);
				}

				code.add(new PDM.PUSH(funFrame.parsSize - 4, loc));
				code.add(new PDM.RETN(funFrame, loc));  // return from function

				attrAST.attrCode.put(funDef, code);
				return code;
			}

//			@Override
//			public List<PDM.CodeInstr> visit(final AST.ParDef parDef, final Mem.Frame frame) {  // we don't actually need to do anything here
//				System.out.println("In ParDef: " + parDef);
//				List<PDM.CodeInstr> code = new LinkedList<>();
////				Mem.Access relAccess = attrAST.attrVarAccess.get(parDef);
////
////				if (relAccess != null) {
////					code.add(new PDM.LABEL(parDef.name + frame.depth, null));
////				}
//
//				attrAST.attrCode.put(parDef, code);
//				return code;
//			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.VarDef varDef, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				List<PDM.DataInstr> data = new LinkedList<>();
				Mem.Access access = attrAST.attrVarAccess.get(varDef);
				Report.Locatable loc = attrAST.attrLoc.get(varDef);

				String label = ":" + labelCounter++;
				if (access instanceof Mem.AbsAccess) {  // global variable
					// code
					code.add(new PDM.NAME(varDef.name, loc));
					code.add(new PDM.NAME(label, loc));
					code.add(new PDM.INIT(loc));
					// data
					data.add(new PDM.LABEL(varDef.name, loc));
					data.add(new PDM.SIZE(access.size, loc));
					data.add(new PDM.LABEL(label, loc));
				} else if (access instanceof Mem.RelAccess) {  // local variable
					// code
					code.add(new PDM.REGN(PDM.REGN.Reg.FP, loc));
					code.add(new PDM.PUSH(((Mem.RelAccess) access).offset, loc));
					code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));
					code.add(new PDM.NAME(label, loc));
					code.add(new PDM.INIT(loc));
					// data
					data.add(new PDM.LABEL(label, loc));
				}

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
				Mem.Access access = attrAST.attrVarAccess.get(def);
				if (access == null) {
					access = attrAST.attrParAccess.get(def);
				}

				if (access instanceof Mem.RelAccess relAccess) {
					code.add(new PDM.REGN(PDM.REGN.Reg.FP, loc));  // start with current FP

					// adjust FP to the FP of the function where the variable is defined
					int depthDiff = frame.depth - relAccess.depth;
					for (int i = 0; i < depthDiff; i++) {
						code.add(new PDM.LOAD(loc));  // load static link from FP
					}
					code.add(new PDM.PUSH(relAccess.offset, loc));
					code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));
					code.add(new PDM.LOAD(loc));  // load the value of the variable
				} else if (access instanceof Mem.AbsAccess absAccess) {
					code.add(new PDM.NAME(absAccess.name, loc));
					code.add(new PDM.LOAD(loc));  // load the value of the variable
				}

				attrAST.attrCode.put(varExpr, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.LetStmt letStmt, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();

				for (AST.Def def : letStmt.defs) {
					if (def instanceof AST.FunDef) {
						def.accept(this, frame);
					} else {
						List<PDM.CodeInstr> varDefCode = def.accept(this, frame);
						code.addAll(varDefCode);
					}
				}

				for (AST.Stmt stmt : letStmt.stmts) {
					List<PDM.CodeInstr> stmtCode = stmt.accept(this, frame);
					code.addAll(stmtCode);
				}

				attrAST.attrCode.put(letStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.ExprStmt exprStmt, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(exprStmt);

				List<PDM.CodeInstr> exprCode = exprStmt.expr.accept(this, frame);
				code.addAll(exprCode);

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
				List<PDM.CodeInstr> dstCleanedExprCode = new LinkedList<>(dstExprCode);
				dstCleanedExprCode.removeLast();  // removes the unnecessary LOAD command - we don't need to load the value of the variable we're assigning to
				code.addAll(dstCleanedExprCode);

				code.add(new PDM.SAVE(loc));

				attrAST.attrCode.put(assignStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.CallExpr callExpr, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				AST.FunDef def = (AST.FunDef) attrAST.attrDef.get(callExpr);
				Mem.Frame funFrame = attrAST.attrFrame.get(def);  // get the Frame of the function that's being called
				Report.Locatable loc = attrAST.attrLoc.get(callExpr);

				for (AST.Expr arg : callExpr.args) {
					List<PDM.CodeInstr> argCode = arg.accept(this, frame);
					code.addAll(argCode);
				}

				code.add(new PDM.REGN(PDM.REGN.Reg.FP, loc));
//				code.add(new PDM.LOAD(loc));  // TODO err, this messes up nested functions
				String funName = funFrame.name;
				if (funNameCount.containsKey(funName) && funNameCount.get(funName) > 0) {
					funName = funFrame.name + ":" + funNameCount.get(funName);
				}
				code.add(new PDM.NAME(funName, loc));  // push the FULL name of the function, eg. 'main.f1:1', not just 'f1' or 'main.f1'
				code.add(new PDM.CALL(funFrame, loc));

				attrAST.attrCode.put(callExpr, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.AtomExpr atomExpr, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				List<PDM.DataInstr> data = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(atomExpr);

				if (atomExpr.type == AST.AtomExpr.Type.STRCONST) {
					String strValue = atomExpr.value.substring(1, atomExpr.value.length() - 1);  // remove outer double quotes from the string
					String label = "str:" + labelCounter++;

					code.add(new PDM.NAME(label, loc));

					data.add(new PDM.LABEL(label, loc));
					for (int i = 0; i < strValue.length(); i++) {
						char c = strValue.charAt(i);
						if (c == '\\') {
							i++;
							switch (strValue.charAt(i)) {
								case 'n':
									data.add(new PDM.DATA((int) '\n', loc));
									break;
								case '\"':
									data.add(new PDM.DATA((int) '\"', loc));
									break;
								case '\\':
									data.add(new PDM.DATA((int) '\\', loc));
									break;
								default:
									if (Character.isDigit(strValue.charAt(i))) {
										String hex = strValue.substring(i, i+2);
										int ascii = Integer.parseInt(hex, 16);
										data.add(new PDM.DATA(ascii, loc));
										i++;
									} else {
										System.out.println("Undefined escape sequence (CodeGen > Generator > visit(AST.AtomExpr...): \\" + strValue.charAt(i));
									}
							}
						} else {
							data.add(new PDM.DATA((int) c, loc));
						}
					}
					data.add(new PDM.DATA(0, loc));  // null-terminate the string
					attrAST.attrData.put(atomExpr, data);
				} else {
					code.add(new PDM.PUSH(Integer.parseInt(atomExpr.value), loc));
				}

				attrAST.attrCode.put(atomExpr, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.BinExpr binExpr, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(binExpr);

				List<PDM.CodeInstr> fstExprCode = binExpr.fstExpr.accept(this, frame);
				List<PDM.CodeInstr> sndExprCode = binExpr.sndExpr.accept(this, frame);
				code.addAll(fstExprCode);
				code.addAll(sndExprCode);

				code.add(new PDM.OPER(convertOperASTtoPDM(binExpr.oper), loc));

				attrAST.attrCode.put(binExpr, code);
				return code;
			}

			private PDM.OPER.Oper convertOperASTtoPDM(AST.BinExpr.Oper oper) {
                return switch(oper) {
					case ADD -> PDM.OPER.Oper.ADD;
					case SUB -> PDM.OPER.Oper.SUB;
					case MUL -> PDM.OPER.Oper.MUL;
					case DIV -> PDM.OPER.Oper.DIV;
					case MOD -> PDM.OPER.Oper.MOD;
					case OR -> PDM.OPER.Oper.OR;
					case AND -> PDM.OPER.Oper.AND;
					case EQU -> PDM.OPER.Oper.EQU;
					case NEQ -> PDM.OPER.Oper.NEQ;
					case GTH -> PDM.OPER.Oper.GTH;
					case LTH -> PDM.OPER.Oper.LTH;
					case GEQ -> PDM.OPER.Oper.GEQ;
					case LEQ -> PDM.OPER.Oper.LEQ;
				};
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.UnExpr unExpr, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(unExpr);

				if (unExpr.oper != AST.UnExpr.Oper.MEMADDR) {
					List<PDM.CodeInstr> exprCode = unExpr.expr.accept(this, frame);
					code.addAll(exprCode);
				}

				switch (unExpr.oper) {
					case NOT -> code.add(new PDM.OPER(PDM.OPER.Oper.NOT, loc));
					case ADD -> {}  // for ADD do nothing
					case SUB -> {
						// negate the value of the expression
						code.add(new PDM.PUSH(-1, loc));
						code.add(new PDM.OPER(PDM.OPER.Oper.MUL, loc));
					}
					case VALUEAT -> code.add(new PDM.LOAD(loc));  // load the value from the address
					case MEMADDR -> {
						if (unExpr.expr instanceof AST.VarExpr varExpr) {
							AST.VarDef varDef = (AST.VarDef) attrAST.attrDef.get(varExpr);
							Mem.Access access = attrAST.attrVarAccess.get(varDef);
							if (access instanceof Mem.RelAccess relAccess) {
								code.add(new PDM.REGN(PDM.REGN.Reg.FP, loc));
								int depthDiff = frame.depth - relAccess.depth;
								for (int i = 0; i < depthDiff; i++) {
									code.add(new PDM.LOAD(loc));  // load static link from FP
								}
								code.add(new PDM.PUSH(relAccess.offset, loc));
								code.add(new PDM.OPER(PDM.OPER.Oper.ADD, loc));
							} else if (access instanceof Mem.AbsAccess absAccess) {
								code.add(new PDM.NAME(absAccess.name, loc));
							}
						}
					}
				}

				attrAST.attrCode.put(unExpr, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.IfStmt ifStmt, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(ifStmt);

				int counter = labelCounter++;

				// prepare for a conditional jump
				code.addAll(ifStmt.cond.accept(this, frame));
				code.add(new PDM.NAME("then:" + counter, loc));
				code.add(new PDM.NAME("else:" + counter, loc));
				code.add(new PDM.CJMP(loc));  // if false, jump to the else part, otherwise the then part

				// execute the then part if the condition was true
				code.add(new PDM.LABEL("then:" + counter, loc));
				code.addAll(ifStmt.thenStmts.accept(this, frame));
				code.add(new PDM.NAME("endif:" + counter, loc));
				code.add(new PDM.UJMP(loc));  // jump to the end of the if statement

				// execute the else part if the condition was false
				code.add(new PDM.LABEL("else:" + counter, loc));
				code.addAll(ifStmt.elseStmts.accept(this, frame));

				// label marking the end of the if statement
				code.add(new PDM.LABEL("endif:" + counter, loc));

				attrAST.attrCode.put(ifStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.WhileStmt whileStmt, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				Report.Locatable loc = attrAST.attrLoc.get(whileStmt);

				int counter = labelCounter++;

				// label for the start of the loop to jump back to
				code.add(new PDM.LABEL("while:" + counter, loc));

				// prepare for a conditional jump
				code.addAll(whileStmt.cond.accept(this, frame));
				code.add(new PDM.NAME("dowhile:" + counter, loc));
				code.add(new PDM.NAME("endwhile:" + counter, loc));
				code.add(new PDM.CJMP(loc));  // if false, jump to the end, otherwise execute the loop body

				// prepare for the loop body
				code.add(new PDM.LABEL("dowhile:" + counter, loc));  // label for the start of the loop body
				code.addAll(whileStmt.stmts.accept(this, frame));  // generate code for the loop body

				// unconditional jump back to the start of the loop
				code.add(new PDM.NAME("while:" + counter, loc));
				code.add(new PDM.UJMP(loc));

				// label marking the end of the loop
				code.add(new PDM.LABEL("endwhile:" + counter, loc));

				attrAST.attrCode.put(whileStmt, code);
				return code;
			}

			@Override
			public List<PDM.CodeInstr> visit(final AST.Nodes nodes, final Mem.Frame frame) {
				List<PDM.CodeInstr> code = new LinkedList<>();
				for (Object objNode : nodes) {
					AST.Node node = (AST.Node) objNode;
					List<PDM.CodeInstr> nodeCode = node.accept(this, frame);
					if (nodeCode != null) {
						code.addAll(nodeCode);
					}
				}
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
