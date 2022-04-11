package cd4017be.dfc.lang.type;

import static cd4017be.dfc.lang.type.Primitive.*;
import static java.lang.System.identityHashCode;

import java.io.IOException;
import java.io.Writer;
import java.nio.BufferUnderflowException;
import java.nio.CharBuffer;
import java.util.*;

/**
 * @author CD4017BE */
public class Types {

	private static final ArrayList<Struct> STRUCTS = new ArrayList<>();
	private static final HashMap<Type, Type> TYPES = new HashMap<>();
	public static final Struct VOID = new Struct(new Type[0], new String[0], -1);

	static int PTR_SIZE = 8, FUN_SIZE = 8;

	public static Pointer ARRAYPTR(Type elem) {
		return new Pointer(0).to(VECTOR(elem, 0, false));
	}

	public static Function FUNCTION(Type ret, Type[] par, String[] names) {
		return unique(new Function(ret, par, names));
	}

	public static Vector VECTOR(Type elem, int count, boolean simd) {
		return unique(new Vector(elem, count, simd));
	}

	public static Struct STRUCT(Type[] types, String[] names) {
		if (types.length == 0) return VOID;
		Struct s = unique(new Struct(types, names, STRUCTS.size()));
		if (s.id == STRUCTS.size()) STRUCTS.add(s);
		return s;
	}

	public static Struct OPAQUE(String name) {
		Struct s = unique(new Struct(VOID.elements, new String[] {name}, STRUCTS.size()));
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
			yield STRUCT(elem.toArray(Type[]::new), null);
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
				if (skipWhitespace(s).get() == ')') yield FUNCTION(t, VOID.elements, VOID.names);
				s.position(s.position() - 1);
				ArrayList<Type> elem = new ArrayList<>();
				do {
					elem.add(parse(s));
				} while(s.get() == ',');
				if (s.get(s.position() - 1) != ')')
					throw new IllegalArgumentException(") expected: ");
				yield FUNCTION(t, elem.toArray(Type[]::new), new String[elem.size()]);
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

	static int[] nameIndex(String[] names) {
		if (names == null) return null;
		int l = names.length;
		class E { String name; int idx; }
		E[] arr = new E[l];
		for (int i = 0; i < l; i++) {
			E e = arr[i] = new E();
			e.name = names[i];
			e.idx = i;
		}
		Arrays.sort(arr, (a, b) -> a.name.compareTo(b.name));
		int[] idx = new int[l << 1];
		for (int i = 0; i < l; i++) {
			names[i] = arr[i].name;
			idx[l + (idx[i] = arr[i].idx)] = i;
		}
		return idx;
	}

	public static void writeTypeDefs(Writer w) throws IOException {
		for (Struct s : STRUCTS) s.define(w);
	}

	public static void clear() {
		TYPES.clear();
		STRUCTS.clear();
		Function.CUR_FUNCTION = null;
	}

}
