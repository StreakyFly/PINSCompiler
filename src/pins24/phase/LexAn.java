package pins24.phase;

import java.io.*;
import java.util.Map;
import java.util.Objects;

import pins24.common.*;

/**
 * Leksikalni analizator.
 */
public class LexAn implements AutoCloseable {

	/** Izvorna datoteka. */
	private final Reader srcFile;

	/**
	 * Ustvari nov leksikalni analizator.
	 *
	 * @param srcFileName Ime izvorne datoteke.
	 */
	public LexAn(final String srcFileName) {
		try {
			srcFile = new BufferedReader(new InputStreamReader(new FileInputStream(new File(srcFileName))));
			nextChar(); // Pripravi prvi znak izvorne datoteke (glej {@link nextChar}).
		} catch (FileNotFoundException __) {
			throw new Report.Error("Source file '" + srcFileName + "' not found.");
		}
	}

	@Override
	public void close() {
		try {
			srcFile.close();
		} catch (IOException __) {
			throw new Report.Error("Cannot close source file.");
		}
	}

	/** Trenutni znak izvorne datoteke (glej {@link nextChar}). */
	private int buffChar = '\n';

	/** Vrstica trenutnega znaka izvorne datoteke (glej {@link nextChar}). */
	private int buffCharLine = 0;

	/** Stolpec trenutnega znaka izvorne datoteke (glej {@link nextChar}). */
	private int buffCharColumn = 0;

	/**
	 * Prebere naslednji znak izvorne datoteke.
	 *
	 * Izvorno datoteko beremo znak po znak. Trenutni znak izvorne datoteke je
	 * shranjen v spremenljivki {@link buffChar}, vrstica in stolpec trenutnega
	 * znaka izvorne datoteke sta shranjena v spremenljivkah {@link buffCharLine} in
	 * {@link buffCharColumn}.
	 *
	 * Zacetne vrednosti {@link buffChar}, {@link buffCharLine} in
	 * {@link buffCharColumn} so {@code '\n'}, {@code 0} in {@code 0}: branje prvega
	 * znaka izvorne datoteke bo na osnovi vrednosti {@code '\n'} spremenljivke
	 * {@link buffChar} prvemu znaku izvorne datoteke priredilo vrstico 1 in stolpec
	 * 1.
	 *
	 * Pri branju izvorne datoteke se predpostavlja, da je v spremenljivki
	 * {@link buffChar} ves "cas veljaven znak. Zunaj metode {@link nextChar} so vse
	 * spremenljivke {@link buffChar}, {@link buffCharLine} in
	 * {@link buffCharColumn} namenjene le branju.
	 *
	 * Vrednost {@code -1} v spremenljivki {@link buffChar} pomeni konec datoteke
	 * (vrednosti spremenljivk {@link buffCharLine} in {@link buffCharColumn} pa
	 * nista ve"c veljavni).
	 */
	private void nextChar() {
		try {
			switch (buffChar) {
			case -2: // Noben znak "se ni bil prebran.
				buffChar = srcFile.read();
				buffCharLine = buffChar == -1 ? 0 : 1;
				buffCharColumn = buffChar == -1 ? 0 : 1;
				return;
			case -1: // Konec datoteke je bil "ze viden.
				return;
			case '\n': // Prejsnji znak je koncal vrstico, zacne se nova vrstica.
				buffChar = srcFile.read();
				buffCharLine = buffChar == -1 ? buffCharLine : buffCharLine + 1;
				buffCharColumn = buffChar == -1 ? buffCharColumn : 1;
				return;
			case '\t': // Prejsnji znak je tabulator, ta znak je morda potisnjen v desno.
				buffChar = srcFile.read();
				while (buffCharColumn % 8 != 0)
					buffCharColumn += 1;
				buffCharColumn += 1;
				return;
			default: // Prejsnji znak je brez posebnosti.
				buffChar = srcFile.read();
				buffCharColumn += 1;
				return;
			}
		} catch (IOException __) {
			throw new Report.Error("Cannot read source file.");
		}
	}

	/**
	 * Trenutni leksikalni simbol.
	 *
	 * "Ce vrednost spremenljivke {@code buffToken} ni {@code null}, je simbol "ze
	 * prebran iz vhodne datoteke, ni pa "se predan naprej sintaksnemu analizatorju.
	 * Ta simbol je dostopen z metodama {@link peekToken} in {@link takeToken}.
	 */
	private Token buffToken = null;

	private static final Map<String, Token.Symbol> keywords = Map.ofEntries(
			Map.entry("var", Token.Symbol.VAR),
			Map.entry("fun", Token.Symbol.FUN),
			Map.entry("if", Token.Symbol.IF),
			Map.entry("then", Token.Symbol.THEN),
			Map.entry("else", Token.Symbol.ELSE),
			Map.entry("while", Token.Symbol.WHILE),
			Map.entry("do", Token.Symbol.DO),
			Map.entry("let", Token.Symbol.LET),
			Map.entry("in", Token.Symbol.IN),
			Map.entry("end", Token.Symbol.END)
	);

	private static final Map<String, Token.Symbol> symbols = Map.ofEntries(
			Map.entry("=", Token.Symbol.ASSIGN),
			Map.entry(",", Token.Symbol.COMMA),
			Map.entry("&&", Token.Symbol.AND),
			Map.entry("||", Token.Symbol.OR),
			Map.entry("!", Token.Symbol.NOT),
			Map.entry("==", Token.Symbol.EQU),
			Map.entry("!=", Token.Symbol.NEQ),
			Map.entry(">", Token.Symbol.GTH),
			Map.entry("<", Token.Symbol.LTH),
			Map.entry(">=", Token.Symbol.GEQ),
			Map.entry("<=", Token.Symbol.LEQ),
			Map.entry("+", Token.Symbol.ADD),
			Map.entry("-", Token.Symbol.SUB),
			Map.entry("*", Token.Symbol.MUL),
			Map.entry("/", Token.Symbol.DIV),
			Map.entry("%", Token.Symbol.MOD),
			Map.entry("^", Token.Symbol.PTR),
			Map.entry("(", Token.Symbol.LPAREN),
			Map.entry(")", Token.Symbol.RPAREN)
	);

	/**
	 * Prebere naslednji leksikalni simbol, ki je nato dostopen preko metod
	 * {@link peekToken} in {@link takeToken}.
	 */
	private void nextToken() {
		while (true) {
			switch (buffChar) {
				case -1:  // end of file
					buffToken = new Token(new Report.Location(buffCharLine, buffCharColumn), Token.Symbol.EOF, "EOF");
					return;
				case '#':  // comment
					handleComment();  // skip comment
					break;
				case ' ', '\n', '\r', '\t':  // whitespace
					nextChar();  // skip whitespace
					break;
				case '\'':  // character
					handleCharLiteral();
					return;
				case '\"':  // string
					handleStringLiteral();
					return;
				default:
					if (isNumber(String.valueOf((char) buffChar))) {  // number
						handleNumber();
						return;
					} else if (isSpecialCharacter((char) buffChar)) {  // symbol
						handleSymbol();
						return;
					} else {  // keyword or identifier
						handleKeywordOrIdentifier();
						return;
					}
			}
		}
	}

	/**
	 * Handles comments by skipping them.
	 */
	private void handleComment() {
		while (buffChar != '\n' && buffChar != '\r' && buffChar != -1) {
			nextChar();
		}
	}

	/**
	 * Handles character literals and creates a token.
	 */
	private void handleCharLiteral() {
		Report.Location startLocation = new Report.Location(buffCharLine, buffCharColumn);
		nextChar();  // consume the starting quote

		String charValue;
		if (buffChar == '\\') {  // escape sequence
			charValue = handleEscapeSequence();
		} else if (buffChar == '\n' || buffChar == -1) {  // not a written '\n', but an actual new line (Enter)
			throw new Report.Error(new Report.Location(buffCharLine, buffCharColumn - 1, buffCharLine, buffCharColumn) + " Character literal not closed: '");
		} else if (isValidCharLiteral((char) buffChar)) {  // regular ASCII character
			charValue = String.valueOf((char) buffChar);
		} else {
			throw new Report.Error(new Report.Location(buffCharLine, buffCharColumn) + " Invalid character literal: '" + (char) buffChar + "'" + " (" + buffChar + ")");
		}

		nextChar();  // move past the character or the escape sequence
		if (buffChar != '\'') { // ensure it ends with a closing quote
			if (Objects.equals(charValue, "'")) {
				throw new Report.Error(getLocationRange(startLocation) + " Character literal can not be empty: ''");
			} else if (Objects.equals(charValue, "\\'")) {
				throw new Report.Error(getLocationRange(startLocation) + " Character literal not closed because of the escape sequence: '\\'");
			} else {
				throw new Report.Error(getLocationRange(startLocation) + " Character literal not closed or contains more than one character: '" + charValue);
			}
		}

		nextChar();  // consume the ending quote
		buffToken = new Token(getLocationRange(startLocation), Token.Symbol.CHARCONST, "'" + charValue + "'");
	}

	/**
	 * Handles string literals and creates a token.
	 */
	private void handleStringLiteral() {
		Report.Location startLocation = new Report.Location(buffCharLine, buffCharColumn);
		nextChar();  // consume the starting double quote

		StringBuilder stringValue = new StringBuilder();
		while (buffChar != '\"') {  // until the ending double quote
			if (buffChar == '\\') {  // escape sequences
				stringValue.append(handleEscapeSequence());
			} else if (buffChar == '\n' || buffChar == -1) {  // not a written "...\n..." in the string, but an actual new line (Enter)
				if (stringValue.toString().endsWith("\\\"")) {
					throw new Report.Error(getLocationRange(startLocation) + " String literal not closed because of the escape sequence: '\"" + stringValue + "'");
				} else {
					throw new Report.Error(getLocationRange(startLocation) + " String literal not closed: '\"" + stringValue + "'");
				}
			} else if (isValidCharLiteral((char) buffChar)) {
				stringValue.append((char) buffChar);
			} else {
				throw new Report.Error(new Report.Location(buffCharLine, buffCharColumn) + " Invalid character in string literal: '" + (char) buffChar + "'" + " (" + buffChar + ")");
			}
			nextChar();
		}
		nextChar();  // consume the ending double quote
		buffToken = new Token(getLocationRange(startLocation), Token.Symbol.STRINGCONST, "\"" + stringValue + "\"");
	}

	/**
	 * Handles numbers and creates a token.
	 */
	private void handleNumber() {
		Report.Location startLocation = new Report.Location(buffCharLine, buffCharColumn);
		StringBuilder sb = new StringBuilder();
		while (Character.isDigit(buffChar)) {
			sb.append((char) buffChar);
			nextChar();
		}

		if (!isSpecialCharacter((char) buffChar)) {  // if the number is followed by a non-special character, for example: 123a
			throw new Report.Error(new Report.Location(buffCharLine, buffCharColumn - sb.length(), buffCharLine, buffCharColumn + 1) + " Invalid token: \"" + sb + (char) buffChar + "\";" + " Character '" + (char) buffChar + "' can not be used after a number (" + sb + ")");
		}

		buffToken = new Token(getLocationRange(startLocation), Token.Symbol.INTCONST, sb.toString());
	}

	/**
	 * Handles symbols and creates a token.
	 */
	private void handleSymbol() {
		char currentChar = (char) buffChar;
		Report.Location startLocation = new Report.Location(buffCharLine, buffCharColumn);

		nextChar();
		String potentialDoubleSymbol = String.valueOf(currentChar) + (char) buffChar;

		Token.Symbol doubleCharSymbol = symbols.get(potentialDoubleSymbol);
		Token.Symbol singleCharSymbol = symbols.get(String.valueOf(currentChar));

		if (doubleCharSymbol != null) {  // handle double character symbol
			buffToken = new Token(new Report.Location(startLocation.begLine(), startLocation.begColumn(), buffCharLine, buffCharColumn), doubleCharSymbol, potentialDoubleSymbol);
			nextChar();  // advance again, since we've confirmed the second character is part of the symbol
		}
		else if (singleCharSymbol != null) {  // handle single character symbol
			buffToken = new Token(getLocationRange(startLocation), singleCharSymbol, String.valueOf(currentChar));
		}
		else {  // illegal/incorrectly used character
			if (isIllegalCharacter(currentChar)) {
				throw new Report.Error(getLocationRange(startLocation) + " Illegal character: '" + currentChar + "'");
			} else {  // if a single "&" or "|" was used instead of two
				throw new Report.Error(getLocationRange(startLocation) + " Incorrectly used symbol: '" + currentChar + "'" + ". Did you mean '" + currentChar + currentChar + "'?");
			}
		}
	}

	/**
	 * Handles keywords and identifiers and creates a token.
	 */
	private void handleKeywordOrIdentifier() {
		StringBuilder sb = new StringBuilder();
		while (!isSpecialCharacter((char) buffChar)) {
			sb.append((char) buffChar);
			nextChar();
		}
		String token = sb.toString();
		Token.Symbol symbol = keywords.get(token);
		Report.Location locationRange = new Report.Location(buffCharLine, buffCharColumn - token.length(), buffCharLine, buffCharColumn - 1);

		if (symbol != null) {  // keyword
			buffToken = new Token(locationRange, symbol, token);
		}
		else if (isName(token)) {  // identifier
			buffToken = new Token(locationRange, Token.Symbol.IDENTIFIER, token);
		}
		else {  // contains invalid characters
			throw new Report.Error(locationRange + " Token contains one or more invalid characters: \"" + token + "\"");
		}
	}

	/**
	 * Handles escape sequences.
	 *
	 * @return The character represented by the escape sequence.
	 */
	private String handleEscapeSequence() {
		nextChar();  // consume the backslash
        return switch (buffChar) {
            case '\'', '\"', '\\' -> "\\" + (char) buffChar;
            case 'n' -> "\\n";
            default -> "\\" + handleHexadecimalEscape();
        };
	}

	/**
	 * Handles hexadecimal escape sequences.
	 *
	 * @return The character represented by the hexadecimal escape sequence.
	 */
	private String handleHexadecimalEscape() {
		if (Character.digit(buffChar, 16) == -1) {
			throw new Report.Error(new Report.Location(buffCharLine, buffCharColumn - 1, buffCharLine, buffCharColumn + 1) + " Invalid escape sequence: '\\" + (char) buffChar + "...");
		}
		String hex = "" + (char) buffChar;
		nextChar();  // move to the second hexadecimal digit
		if (Character.digit(buffChar, 16) == -1) {
			throw new Report.Error(new Report.Location(buffCharLine, buffCharColumn - 2, buffCharLine, buffCharColumn + 1) + " Invalid/Incomplete hexadecimal escape sequence: '\\" + hex + (char) buffChar + "'");
		}
		hex += (char) buffChar;
//		return (char) Integer.parseInt(hex, 16);
		return hex;
	}

	/**
	 * Preveri, ali je znak poseben.
	 *
	 * @param c Znak.
	 * @return boolean, ki pove, ali je znak poseben ali ne.
	 */
	private boolean isSpecialCharacter(char c) {
		char[] specialCharacters = {'=', ',', '&', '|', '!', '>', '<', '+', '-', '*', '/', '%', '^', '(', ')',
				'#', ' ', '\n', '\r', '\t', '\'', '\"', '\uFFFF'};  // '\uFFFF' is the end of file character (-1)
		for (char specialCharacter : specialCharacters) {
			if (c == specialCharacter) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Preveri, ali je znak neveljaven (jezik ga ne podpira).
	 *
	 * @param c Znak.
	 * @return boolean, ki pove, ali je znak neveljaven ali ne.
	 */
	private boolean isIllegalCharacter(char c) {
//		return !String.valueOf(c).matches("[a-zA-Z0-9_+\\-*/%^()=,|&!><]");
		return !String.valueOf(c).matches("[a-zA-Z0-9_+\\-*/%^()=,|&!><\\s]");  // also includes whitespaces
	}

	/**
	 * Preveri, ali je niz veljavno ime {@code IDENTIFIER}
	 *
	 * @param str Niz.
	 * @return boolean, ki pove, ali je niz veljavno ime ali ne.
	 */
	private boolean isName(String str) {
		return str.matches("[a-zA-Z_][a-zA-Z0-9_]*");
	}

	/**
	 * Preveri, ali je niz veljavno stevilo {@code INTCONST}.
	 *
	 * @param str Niz.
	 * @return boolean, ki pove, ali je niz veljavno stevilo ali ne.
	 */
	private boolean isNumber(String str) {
		return str.matches("[0-9]+");
	}

	/**
	 * Preveri, ali je znak veljaven za {@code CHARCONST} in {@code STRINGCONST}.
	 *
	 * @param c Znak.
	 * @return boolean, ki pove, ali je znak veljaven ali ne.
	 */
	private boolean isValidCharLiteral(char c) {
		return c >= 32 && c <= 126 || c == '\n' || c == '\r';
	}

	/**
	 * Vrne lokacijo, ki se zacne s {@code startLocation} in konca s trenutno lokacijo.
	 * {@link nextChar} more biti klican pred klicem te metode.
	 *
	 * @param startLocation Zacetna lokacija.
	 * @return Lokacija, ki se zacne s {@code startLocation} in konca s trenutno lokacijo.
	 */
	private Report.Location getLocationRange(Report.Location startLocation) {
		return new Report.Location(startLocation.begLine(), startLocation.begColumn(), buffCharLine, buffCharColumn - 1);
	}

	/**
	 * Vrne trenutni leksikalni simbol, ki ostane v lastnistvu leksikalnega
	 * analizatorja.
	 *
	 * @return Leksikalni simbol.
	 */
	public Token peekToken() {
		if (buffToken == null)
			nextToken();
		return buffToken;
	}

	/**
	 * Vrne trenutni leksikalni simbol, ki preide v lastnistvo klicoce kode.
	 *
	 * @return Leksikalni simbol.
	 */
	public Token takeToken() {
		if (buffToken == null)
			nextToken();
		final Token thisToken = buffToken;
		buffToken = null;
		return thisToken;
	}

	// --- ZAGON ---

	/**
	 * Zagon leksikalnega analizatorja kot samostojnega programa.
	 *
	 * @param cmdLineArgs Argumenti v ukazni vrstici.
	 */
	public static void main(final String[] cmdLineArgs) {
		System.out.println("This is PINS'24 compiler (lexical analysis):");

		try {
			if (cmdLineArgs.length == 0)
				throw new Report.Error("No source file specified in the command line.");
			if (cmdLineArgs.length > 1)
				Report.warning("Unused arguments in the command line.");

			try (LexAn lexAn = new LexAn(cmdLineArgs[0])) {
				while (lexAn.peekToken().symbol() != Token.Symbol.EOF) {
					System.out.println(lexAn.takeToken());
				}
				System.out.println(lexAn.takeToken());
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
