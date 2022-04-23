package cd4017be.dfc.lang;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.lang.ProcessBuilder.Redirect;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import cd4017be.dfc.lang.type.*;
import cd4017be.util.ScopedHashMap;

/**Used to parse C-header files and turn all their declarations into Signals.
 * @author CD4017BE */
public class HeaderParser {

	private final HashMap<Integer, int[]> macros = new HashMap<>();
	private final ScopedHashMap<Integer, CType> types = new ScopedHashMap<>();
	private final HashMap<String, Integer> map = new HashMap<>();
	private final ArrayList<String> names = new ArrayList<>();
	private IntBuffer tokens = IntBuffer.allocate(4096);
//	private final Type[] primitives = {
//		Primitive.UWORD, Primitive.WORD, Primitive.UWORD,
//		Primitive.SHORT, Primitive.SHORT, Primitive.USHORT,
//		Primitive.INT, Primitive.INT, Primitive.UINT,
//		Primitive.LONG, Primitive.LONG, Primitive.ULONG,
//		Primitive.LONG, Primitive.LONG, Primitive.ULONG,
//		Primitive.FLOAT, Primitive.DOUBLE, Primitive.DOUBLE,
//		Types.VOID
//	};

	/**Run the given C-header file through cpp and parse its output.
	 * The results can be obtained afterwards with {@link #getDeclarations(HashMap)}.
	 * @param file C-header to process
	 * @param cpp whether to feed the file through cpp first.
	 * @throws IOException */
	public void processHeader(File file, boolean cpp) throws IOException {
		macros.clear();
		types.clear();
		map.clear();
		names.clear();
		tokens.clear();
		Reader r;
		Process p;
		if (cpp) {
			p = new ProcessBuilder("cpp", "-P", "-dD", file.getName())
			.redirectError(Redirect.INHERIT)
			.directory(file.getParentFile())
			.start();
			r = new InputStreamReader(p.getInputStream(), UTF_8);
		} else {
			p = null;
			r = new FileReader(file);
		}
		try (BufferedReader in = new BufferedReader(r)) {
			for (int i = 0; i < HARD_CODED_IDS.length; i++) {
				String s = HARD_CODED_IDS[i];
				names.add(s);
				map.put(s, i);
			}
			for (String s; (s = in.readLine()) != null;)
				processLine(s);
			map.clear();
			if (p != null && p.waitFor() != 0)
				throw new IOException("cpp didn't complete normally");
		} catch(InterruptedException e) {
		} finally {
			if (p != null) p.destroy();
		}
	}

	private void processLine(String s) {
		if (s.isBlank()) return;
		IntBuffer tk = tokens;
		int l = s.length(), tp = tk.position();
		//reserve enough space for all following operations.
		if (tk.remaining() < l) {
			tk = IntBuffer.allocate(Math.max(tp + l, tk.capacity() * 2));
			System.arraycopy(tokens.array(), 0, tk.array(), 0, tp);
			tokens = tk.position(tp);
		}
		for (int p = 0; p < l;) {
			char c = s.charAt(p++), c1 = p < l ? s.charAt(p) : '\0';
			int q, t;
			switch(c) {
			case ' ': continue;
			case '{', '}', '[', ']', '(', ')', ',', ';', '~', '?', ':':
				t = c;
				break;
			case '#':
				if (c1 == '#') {
					t = PASTE;
					p++;
				} else t = c;
				break;
			case '+':
				if (c1 == '+') {
					t = INC;
					p++;
				} else if (c1 == '='){
					t = ADD;
					p++;
				} else t = c;
				break;
			case '-':
				if (c1 == '-') {
					t = DEC;
					p++;
				} else if (c1 == '=') {
					t = SUB;
					p++;
				} else if (c1 == '>') {
					t = ELEM;
					p++;
				} else t = c;
				break;
			case '*':
				if (c1 == '=') {
					t = MUL;
					p++;
				} else t = c;
				break;
			case '/':
				if (c1 == '=') {
					t = DIV;
					p++;
				} else t = c;
				break;
			case '%':
				if (c1 == '=') {
					t = MOD;
					p++;
				} else t = c;
				break;
			case '&':
				if (c1 == '&') {
					t = L_AND;
					p++;
				} else if (c1 == '=') {
					t = AND;
					p++;
				} else t = c;
				break;
			case '|':
				if (c1 == '|') {
					t = L_OR;
					p++;
				} else if (c1 == '=') {
					t = OR;
					p++;
				} else t = c;
				break;
			case '^':
				if (c1 == '=') {
					t = XOR;
					p++;
				} else t = c;
				break;
			case '=':
				if (c1 == '=') {
					t = EQUAL;
					p++;
				} else t = c;
				break;
			case '<':
				if (c1 == '<') {
					c1 = ++p < l ? s.charAt(p) : '\0';
					if (c1 == '=') {
						t = SHL;
						p++;
					} else t = OP_SHL;
				} else if (c1 == '=') {
					t = LT_EQ;
					p++;
				} else t = c;
				break;
			case '>':
				if (c1 == '>') {
					c1 = ++p < l ? s.charAt(p) : '\0';
					if (c1 == '=') {
						t = SHR;
						p++;
					} else t = OP_SHR;
				} else if (c1 == '=') {
					t = GT_EQ;
					p++;
				} else t = c;
				break;
			case '!':
				if (c1 == '=') {
					t = NOT_EQ;
					p++;
				} else t = c;
				break;
			case '.':
				if (c1 == '.') {
					c1 = p + 1 < l ? s.charAt(p + 1) : '\0';
					if (c1 == '.') {
						t = ELIPSIS;
						p += 2;
					} else t = c;
				} else if (Character.isDigit(c1)) {
					p = endNumber(s, q = p - 1);
					t = NUMBER | getIdx(s.substring(q, p));
				} else t = c;
				break;
			case '"': t = STRING;
			case '\'': t = CHAR_LIT;
				for (q = p; p < l; p++) {
					c1 = s.charAt(p);
					if (c1 == c) break;
					if (c1 == '\\') p++;
				}
				t |= getIdx(s.substring(q, p));
				break;
			default:
				if (Character.isDigit(c)) {
					p = endNumber(s, q = p - 1);
					t = NUMBER | getIdx(s.substring(q, p));
				} else if (Character.isJavaIdentifierStart(c)) {
					for (q = p - 1; p < l; p++)
						if (!Character.isJavaIdentifierPart(s.charAt(p)))
							break;
					t = ID | getIdx(s.substring(q, p));
				} else t = c;
			}
			tk.put(t);
		}
		//handle #define directives:
		if (tk.get(tp) != '#') return;
		tk.put(EOF); //so no need to check hasRemaining() every time.
		tk.flip().position(tp + 1);
		int t = tk.get();
		dir: switch(t) {
		case UNDEF:
			if ((t = tk.get()) >= 0) break;
			macros.remove(t);
			tk.clear().position(tp);
			return;
		case DEFINE:
			if ((t = tk.get()) >= 0) break;
			tk.mark();
			int t1 = tk.get(), n = -1;
			if (t1 == '(' && s.charAt(s.indexOf('(') - 1) != ' ') {
				int p = tk.position();
				n = 0;
				do {
					t1 = tk.get();
					if (t1 == ')') break;
					if (t1 >= 0 && t1 != ELIPSIS) break dir;
					n++;
					t1 = tk.get();
				} while(t1 == ',');
				if (t1 != ')') break dir;
				tk.mark();
				while ((t1 = tk.get()) != EOF) {
					if (t1 >= 0) continue;
					for (int j = 0; j < n; j++)
						if (tk.get(p + j * 2) == t1) {
							tk.put(tk.position() - 1, j | REPLACE);
							break;
						}
				}
			}
			tk.reset();
			int[] macro = new int[tk.remaining()];
			macro[0] = n;
			tk.get(macro, 1, macro.length - 1);
			macros.put(t, macro);
			tk.clear().position(tp);
			return;
		default:
			if (t >= 0) return;
		}
		System.err.print("unknown directive: ");
		System.err.println(s);
	}

	private int getIdx(String s) {
		return map.computeIfAbsent(s, n -> {
			names.add(n);
			return names.size() - 1;
		});
	}

	private static int endNumber(CharSequence s, int p) {
		for (int l = s.length(); p < l; p++) {
			char c = s.charAt(p);
			if (c == 'e' || c == 'E' || c == 'p' || c == 'P') {
				c = p + 1 < l ? s.charAt(p + 1) : '\0';
				if (c == '+' || c == '-') p++;
				continue;
			}
			if (c != '.' && !Character.isJavaIdentifierPart(c)) break;
		}
		return p;
	}

	private static final int
	CATEGORY = 0xf000_0000, IDX_MASK = 0xfff_ffff,
	ID = 0x8000_0000, REPLACE = 0x4000_0000,
	NUMBER = 0x1000_0000, CHAR_LIT = 0x2000_0000, STRING = 0x3000_0000,
	OPERATOR = 0x10000, OP_MASK = 0x1f,
	EOF = 0x10000, INC = 0x10001, DEC = 0x10002, L_AND = 0x10003,
	L_OR = 0x10004, OP_SHL = 0x10005, OP_SHR = 0x10006, ELEM = 0x10007,
	ADD = 0x10008, SUB = 0x10009, MUL = 0x1000A, DIV = 0x1000B,
	MOD = 0x1000C, AND = 0x1000D, OR = 0x1000E, XOR = 0x1000F,
	SHL = 0x10010, SHR = 0x10011, ELIPSIS = 0x10012, LT_EQ = 0x10013,
	GT_EQ = 0x10014, NOT_EQ = 0x10015, EQUAL = 0x10016, PASTE = 0x10017;

	private static final String[] OPERATORS = {
		"\n", "++", "--", "&&",
		"||", "<<", ">>", "->",
		"+=", "-=", "*=", "/=",
		"%=", "&=", "|=", "^=",
		"<<=", ">>=", "...", "<=",
		">=", "!=", "==", "##",
		"", "", "", "",
		"", "", "", ""
	};

	@SuppressWarnings("unused")
	private static final int
	EMPTY = ID | 0, DEFINE = ID | 1, UNDEF = ID | 2, TYPEDEF = ID | 3,
	CHAR = ID | 4, INT = ID | 5, FLOAT = ID | 6, DOUBLE = ID | 7,
	SHORT = ID | 8, LONG = ID | 9, SIGNED = ID | 10, UNSIGNED = ID | 11,
	STRUCT = ID | 12, UNION = ID | 13, VOID = ID | 14, ENUM = ID | 15,
	CONST = ID | 16, COMPLEX = ID | 17, BOOL = ID | 18, THREAD_LOCAL = ID | 19,
	ATOMIC = ID | 20, ALIGNAS = ID | 21, VOLATILE = ID | 22, EXTERN = ID | 23,
	STATIC = ID | 24, REGISTER = ID | 25, AUTO = ID | 26, RESTRICT = ID | 27,
	INLINE = ID | 28, NORETURN = ID | 29, SIZEOF = ID | 30;

	private static final String[] HARD_CODED_IDS = {
		"", "define", "undef", "typedef",
		"char", "int", "float", "double",
		"short", "long", "signed", "unsigned",
		"struct", "union", "void", "enum",
		"const", "_Complex", "_Bool", "_Thead_local",
		"_Atomic", "_Alignas", "volatile", "extern",
		"static", "register", "auto", "restrict",
		"inline", "_Noreturn", "sizeof",
	};

	/**Obtains simple macros that expand to numeric expressions
	 * and all global variable, function and type declarations.
	 * @param extDef set of external definitions to fill
	 * @throws IOException if the cpp output could not be parsed */
	public void getDeclarations(ExternalDefinitions extDef) throws IOException {
		for (Entry<Integer, int[]> e : macros.entrySet()) {
			int[] val = e.getValue();
			if (val[0] >= 0 || val.length != 2) continue;
			int i = val[1];
			//TODO evaluate numeric expressions
			if ((i & CATEGORY) != NUMBER) continue;
			String name = names.get(e.getKey() & IDX_MASK);
			if (name.startsWith("_")) continue;
			String text = names.get(i & IDX_MASK);
			extDef.macros.put(name, text);
		}
		macros.clear();
		IntBuffer buf = tokens.put(EOF).flip();
		while (buf.get(buf.position()) != EOF) {
			buf.mark();
			declaration(extDef.include);
		}
		tokens.clear();
		types.clear();
	}

	static final int
	T_CHAR = 0x0001, T_INT = 0x0002, T_FLOAT = 0x0003, T_DOUBLE = 0x0004,
	T_VOID = 0x0005, T_BOOL = 0x0006, T_STRUCT = 0x0007, T_UNION = 0x0008,
	T_ENUM = 0x0009, T_POINTER = 0x000A, T_ARRAY = 0x000B, T_FUNCTION = 0x000C,
	T_LONG = 0x0010, T_LONGLONG = 0x0020, T_SHORT = 0x0030,
	T_SIGNED = 0x0040, T_UNSIGNED = 0x0080, T_COMPLEX = 0x0100,
	T_TYPEDEF = 0x0200, T_EXTERN = 0x0400, T_STATIC = 0x0600,
	T_AUTO = 0x0800, T_REGISTER = 0x0A00,
	T_THREADLOCAL = 0x1000, T_ATOMIC = 0x2000, T_CONST = 0x4000, T_VOLATILE = 0x8000,
	T_RESTRICT = 0x10000, T_INLINE = 0x20000, T_NORETURN = 0x40000,
	T_TYPE = 0x000F, T_STORAGE = 0x0E00,
	T_INHERIT = T_STORAGE | T_INLINE | T_NORETURN;

	public static class CDecl {
		CDecl next;
		CType type;
		String name;
		Signal dfcSignal;
	}

	public static class CType {
		int mods;
		Object content;
		Type dfcType;

		CType() {}
		CType(CType type) {
			this.mods = type.mods;
			this.content = type.content;
		}
	}

	static class CArray extends CType {
		int size = -1;
		CArray(CType type) {
			super(type);
		}
	}

	static class CFunction extends CType {
		CDecl par;
		boolean va_arg;
		CFunction(CType ret) {
			super(ret);
		}
	}

	private void declaration(HashMap<String, CDecl> declarations) throws IOException {
		CType type = type();
		int t;
		do {
			CDecl decl = new CDecl();
			decl.type = new CType(type);
			declarator(decl);
			t = tokens.get();
			if (t == '=') assignmentExpr();
			String name = decl.name;
			if (name != null && !name.startsWith("_"))
				declarations.put(name, decl);
		} while(t == ',');
		if (t == '{') {
			for (int n = 0; n >= 0;) {
				t = tokens.get();
				if (t == '{') n++;
				else if (t == '}') n--;
			}
		} else if (t != ';') throw error();
	}

	private int typeQualifierList(CType type) {
		int t;
		boolean loop = true;
		do
			switch(t = tokens.get()) {
			case CONST -> type.mods |= T_CONST;
			case RESTRICT -> type.mods |= T_RESTRICT;
			case VOLATILE -> type.mods |= T_VOLATILE;
			case ATOMIC -> type.mods |= T_ATOMIC;
			case STATIC -> type.mods |= T_STATIC;
			default -> loop = false;
			}
		while(loop);
		return t;
	}

	private void declarator(CDecl decl) throws IOException {
		int t = tokens.get();
		while(t == '*') {
			CType type = new CType();
			type.mods = decl.type.mods & T_INHERIT | T_POINTER;
			type.content = decl.type;
			decl.type = type;
			t = typeQualifierList(type);
		}
		CType type = decl.type;
		if (t == '(')
			if (peek() != ')') {
				declarator(decl);
				if (tokens.get() != ')') throw error();
			} else back();
		else if (t < 0) {
			if ((type.mods & T_STORAGE) == T_TYPEDEF)
				types.put(t, decl.type);
			decl.name = names.get(t & IDX_MASK);
		} else back();
		for (boolean loop = true; loop;)
			switch(t = tokens.get()) {
			case '[' -> array(type);
			case '(' -> function(type);
			default -> loop = false;
			}
		back();
	}

	private void array(CType type) throws IOException {
		CArray arr = new CArray(type);
		type.content = arr;
		type.mods = type.mods & T_INHERIT | T_ARRAY;
		int t = typeQualifierList(type);
		if (t != ']') {
			if (t != '*') {
				back();
				arr.size = (int)assignmentExpr();
			} else arr.size = 0;
			if (tokens.get() != ']') throw error();
		}
	}

	private void function(CType type) throws IOException {
		types.push();
		CFunction func = new CFunction(type);
		type.content = func;
		type.mods = type.mods & T_INHERIT | T_FUNCTION;
		CDecl decl = func.par = new CDecl(), last = null;
		int t;
		do {
			t = tokens.get();
			if (t == ELIPSIS) {
				func.va_arg = true;
				t = tokens.get();
				break;
			}
			if (t == ')') break;
			back();
			decl.type = type();
			declarator(decl);
			//TODO convert array to pointer
			decl = (last = decl).next = new CDecl();
			t = tokens.get();
		} while(t == ',');
		if (t != ')') throw error();
		if (last == null || (last.type.mods & 15) == T_VOID)
			func.par = null;
		else last.next = null;
		types.pop();
	}

	private long assignmentExpr() {
		for (int n = 0; n >= 0;)
			switch(tokens.get()) {
			case ',', ';' -> n = n == 0 ? -1 : n;
			case '(', '[', '{' -> n++;
			case ')', ']', '}' -> n--;
			default -> {}
			}
		back();
		return 0L; //TODO assignment
	}

	private CType type() throws IOException {
		CType type = new CType();
		for (boolean loop = true; loop;) {
			int t = tokens.get();
			switch(t) {
			case SIGNED -> type.mods |= T_SIGNED;
			case UNSIGNED -> type.mods |= T_UNSIGNED;
			case COMPLEX -> type.mods |= T_COMPLEX;
			case SHORT -> type.mods |= T_SHORT;
			case LONG -> type.mods += T_LONG;
			case INT -> type.mods |= T_INT;
			case CHAR -> type.mods |= T_CHAR;
			case VOID -> type.mods |= T_VOID;
			case FLOAT -> type.mods |= T_FLOAT;
			case DOUBLE -> type.mods |= T_DOUBLE;
			case BOOL -> type.mods |= T_BOOL;
			case THREAD_LOCAL -> type.mods |= T_THREADLOCAL;
			case TYPEDEF -> type.mods |= T_TYPEDEF;
			case EXTERN -> type.mods |= T_EXTERN;
			case STATIC -> type.mods |= T_STATIC;
			case AUTO -> type.mods |= T_AUTO;
			case REGISTER -> type.mods |= T_REGISTER;
			case STRUCT -> {type.mods |= T_STRUCT; struct_union(type);}
			case UNION -> {type.mods |= T_UNION; struct_union(type);}
			case ENUM -> {type.mods |= T_ENUM; enums(type);}
			case ATOMIC -> type.mods |= T_ATOMIC;
			case CONST -> type.mods |= T_CONST;
			case VOLATILE -> type.mods |= T_VOLATILE;
			case RESTRICT -> type.mods |= T_RESTRICT;
			case INLINE -> type.mods |= T_INLINE;
			case NORETURN -> type.mods |= T_NORETURN;
			case ALIGNAS -> align();
			default -> {
				if (t < 0 && (type.mods & T_TYPE) == 0) {
					CType def = types.get(t);
					if (def != null && (def.mods & T_STORAGE) == T_TYPEDEF) {
						type.mods |= def.mods & ~T_STORAGE;
						type.content = def.content;
						break;
					}
				}
				loop = false;
			}
			}
		}
		back();
		return type;
	}

	private void struct_union(CType type) throws IOException {
		CDecl struct;
		int t = tokens.get();
		if (t < 0) {
			CType tp = types.get(t);
			if (tp != null && ((tp.mods ^ type.mods) & T_TYPE) == 0)
				struct = (CDecl)tp.content;
			else {
				struct = new CDecl();
				struct.name = names.get(t & IDX_MASK);
				types.put(t, type);
			}
			t = tokens.get();
		} else struct = new CDecl();
		type.content = struct;
		if (t != '{') {
			back();
			return;
		}
		types.push();
		while(peek() != '}') {
			type = type();
			do {
				CDecl decl = new CDecl();
				decl.type = new CType(type);
				struct = struct.next = decl;
				declarator(decl);
				t = tokens.get();
			} while(t == ',');
			if (t != ';') throw error();
		}
		tokens.get();
		types.pop();
	}

	private void enums(CType type) throws IOException {
		throw error();
	}

	private void align() throws IOException {
		throw error();
	}

	private int peek() {
		return tokens.get(tokens.position());
	}

	private void back() {
		tokens.position(tokens.position() - 1);
	}

	private IOException error() {
		return new IOException("can't parse: " + toString());
	}

//	private long number(int t) {
//		String s = names.get(t & IDX_MASK);
//		if (s.startsWith("0x") || s.startsWith("0X"))
//			return Long.parseLong(s.substring(2), 16);
//		return Long.parseLong(s);
//	}

	@Override
	public String toString() {
		IntBuffer buf = tokens;
		int l = buf.limit();
		buf.limit(buf.position()).reset();
		StringBuilder sb = new StringBuilder();
		while(buf.hasRemaining()) {
			int t = buf.get();
			sb.append(' ');
			int c = t & CATEGORY;
			if (c == 0) {
				if ((t & OPERATOR) == 0) sb.append((char)t);
				else sb.append(OPERATORS[t & OP_MASK]);
				continue;
			}
			String val = names.get(t & IDX_MASK);
			if (c == ID || c == NUMBER)
				sb.append(val);
			else if (c == CHAR)
				sb.append('\'').append(val).append('\'');
			else sb.append('"').append(val).append('"');
		}
		buf.limit(l);
		return sb.toString();
	}

//	private void macroExpand(boolean eval) {
//		IntBuffer src = tokens.duplicate();
//		tokens.clear().position(src.limit());
//		while(src.hasRemaining()) {
//			int t = src.get();
//			if (eval && (t & ~SPACE) == DEFINED) {
//				tokens.put(evalDefined(src) | t & SPACE);
//				continue;
//			}
//			if ((t & ID) == 0 || !expand(macros.get(t & IDX_MASK), src))
//				tokens.put(t);
//		}
//		tokens.flip().position(src.limit());
//	}

//	private boolean expand(Token m, IntBuffer src) {
//		if (!m.defined()) return false;
//		int n = m.macro[0], l = m.macro.length;
//		if (n >= 0) {
//			src.mark();
//			int t = src.get() & ~SPACE;
//			if (t != '(') {
//				src.reset();
//				return false;
//			}
//			throw error();
//			//TODO args
//		}
//		IntBuffer macro = IntBuffer.wrap(m.macro, 1, l - 1);
//		while(macro.hasRemaining()) {
//			int t = macro.get();
//			if ((t & ID) != 0)
//				if ((t & REPLACE) != 0) {
//					throw error();
//					//TODO args
//				} else if (expand(macros.get(t & IDX_MASK), src))
//					continue;
//			tokens.put(t);
//		}
//		return true;
//	}

//	private long evalExpr() {
//		//TODO operator precedence
//		long val = evalSingle();
//		for (int t = nextToken(); t >= 0 && t != ')'; t = nextToken()) {
//			switch(t) {
//			case '+': val += evalSingle(); break;
//			case '-': val -= evalSingle(); break;
//			case '*': val *= evalSingle(); break;
//			case '/': val /= evalSingle(); break;
//			case '%': val %= evalSingle(); break;
//			case '&': val &= evalSingle(); break;
//			case '|': val |= evalSingle(); break;
//			case '^': val ^= evalSingle(); break;
//			case '<': val = val < evalSingle() ? 1 : 0; break;
//			case '>': val = val > evalSingle() ? 1 : 0; break;
//			case EQUAL: val = val == evalSingle() ? 1 : 0; break;
//			case NOT_EQ: val = val != evalSingle() ? 1 : 0; break;
//			case LT_EQ: val = val <= evalSingle() ? 1 : 0; break;
//			case GT_EQ: val = val >= evalSingle() ? 1 : 0; break;
//			case L_AND: val = val != 0 & evalSingle() != 0 ? 1 : 0; break;
//			case L_OR: val = val != 0 | evalSingle() != 0 ? 1 : 0; break;
//			case OP_SHL: val <<= evalSingle(); break;
//			case OP_SHR: val >>= evalSingle(); break;
//			case OP_USHR: val >>>= evalSingle(); break;
//			default: throw error();
//			}
//		}
//		return val;
//	}

//	private long evalSingle() {
//		int t = nextToken();
//		switch(t) {
//		case '(': return evalExpr();
//		case '!': return evalSingle() == 0 ? 1 : 0;
//		case '~': return ~evalSingle();
//		case '-': return -evalSingle();
//		case '+': return evalSingle();
//		}
//		if ((t & CATEGORY) == NUMBER) {
//			String s = macros.get(t & IDX_MASK).text;
//			if (s.startsWith("0x") || s.startsWith("0X"))
//				return Long.parseLong(s.substring(2), 16);
//			return Long.parseLong(s);
//		} else if ((t & CATEGORY) == ID) return 0;
//		throw error();
//	}

//	private IllegalStateException error() {
//		tokens.rewind();
//		throw new IllegalStateException(this.toString());
//	}

}
