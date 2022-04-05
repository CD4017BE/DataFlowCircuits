package cd4017be.dfc.lang.type;

import static cd4017be.dfc.lang.type.Primitive.*;
import static java.lang.System.identityHashCode;

import java.io.IOException;
import java.io.Writer;
import java.nio.BufferUnderflowException;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author CD4017BE */
public class Types {

	private static final ArrayList<Struct> STRUCTS = new ArrayList<>();
	private static final HashMap<Type, Type> TYPES = new HashMap<>();
	public static final Struct VOID = new Struct(new Type[0], -1);

	static int PTR_SIZE = 8, FUN_SIZE = 8;

	public static Function FUNCTION(Type ret, Type... par) {
		return unique(new Function(ret, par));
	}

	public static Vector VECTOR(Type elem, int count, boolean simd) {
		return unique(new Vector(elem, count, simd));
	}

	public static Struct STRUCT(Type... elem) {
		if (elem.length == 0) return VOID;
		Struct s = unique(new Struct(
			elem, elem.length > 1 ? STRUCTS.size() : -1
		));
		if (s.id == STRUCTS.size()) STRUCTS.add(s);
		return s;
	}

	public static Type parseType(String s) {
		CharBuffer buf = CharBuffer.wrap(s);
		try {
			Type t = parse(buf);
			if (buf.hasRemaining())
				throw new IllegalArgumentException("invalid char: ");
			return t;
		} catch(IllegalArgumentException e) {
			throw new IllegalArgumentException(e.getMessage() + buf.flip(), e);
		} catch(BufferUnderflowException e) {
			throw new IllegalArgumentException("unexpected end of string", e);
		}
	}

	private static Type parse(CharBuffer s) {
		int n = parseInt(skipWhitespace(s));
		Type t = switch(s.get()) {
		case 'W' -> UWORD;
		case 'S' -> USHORT;
		case 'I' -> UINT;
		case 'L' -> ULONG;
		case 'w' -> WORD;
		case 's' -> SHORT;
		case 'i' -> INT;
		case 'l' -> LONG;
		case 'f', 'F' -> FLOAT;
		case 'd', 'D' -> DOUBLE;
		case 'b', 'B' -> BOOL;
		case '[' -> {
			Type t1 = parse(s);
			if (s.get() != ']') throw new IllegalArgumentException("] expected: ");
			yield VECTOR(t1, n, false);
		}
		case '{' -> {
			if (n != 0) throw new IllegalArgumentException("invalid char: ");
			if (skipWhitespace(s).get() == '}') yield VOID;
			s.position(s.position() - 1);
			ArrayList<Type> elem = new ArrayList<>();
			do {
				elem.add(parse(s));
			} while(s.get() == ',');
			if (s.get(s.position() - 1) != '}') throw new IllegalArgumentException("} expected: ");
			yield STRUCT(elem.toArray(Type[]::new));
		}
		default -> throw new IllegalArgumentException("invalid char: ");
		};
		if (n > 0 && t.canSimd()) t = VECTOR(t, n, true);
		for(Type nt; skipWhitespace(s).hasRemaining(); t = nt) {
			n = parseInt(s);
			nt = switch(s.get()) {
			default -> null;
			case '*' -> new Pointer(0).to(t);
			case '(' -> {
				if (skipWhitespace(s).get() == ')') yield FUNCTION(t);
				s.position(s.position() - 1);
				ArrayList<Type> elem = new ArrayList<>();
				do {
					elem.add(parse(s));
				} while(s.get() == ',');
				if (s.get(s.position() - 1) != ')')
					throw new IllegalArgumentException(") expected: ");
				yield FUNCTION(t, elem.toArray(Type[]::new));
			}
			};
			if (nt == null) {
				s.position(s.position() - 1);
				break;
			}
			if (n > 0) nt = VECTOR(nt, n, true);
		}
		return t;
	}

	private static CharBuffer skipWhitespace(CharBuffer s) {
		while(s.hasRemaining())
			if (!Character.isWhitespace(s.get()))
				return s.position(s.position() - 1);
		return s;
	}

	private static int parseInt(CharBuffer s) {
		int i = 0;
		while(s.hasRemaining()) {
			char c = s.get();
			if (c < '0' || c > '9') {
				s.position(s.position() - 1);
				return i;
			}
			i = i * 10 + (c - '0');
		}
		return i;
	}

	@SuppressWarnings("unchecked")
	static <T extends Type> T unique(T t) {
		Type t0 = TYPES.putIfAbsent(t, t);
		return t0 == null ? t : (T)t0;
	}

	static <T> int contentIdentityHash(T[] array) {
		int result = 0;
		for (T t : array) result = 31 * result + identityHashCode(t);
		return result;
	}

	static <T> boolean contentIdentical(T[] a, T[] b) {
		if (a.length != b.length) return false;
		for (int i = 0; i < a.length; i++)
			if (a[i] != a[i]) return false;
		return true;
	}

	public static void writeTypeDefs(Writer w) throws IOException {
		for (Struct s : STRUCTS) s.define(w);
	}

	public static void clear() {
		TYPES.clear();
		STRUCTS.clear();
	}

}
