package cd4017be.dfc.lang;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Arrays.copyOfRange;

import java.nio.BufferOverflowException;
import java.nio.CharBuffer;
import java.util.*;

import cd4017be.util.IntLink;

/**
 * @author CD4017BE */
public class Type {

	private static final String names = "?TVZBSILFD";
	/** primitive type ids */
	public static final int
	UNKNOWN = 0, TYPE = 1, //abstract types
	VOID = 2, BOOL = 3, BYTE = 4, SHORT = 5, INT = 6, LONG = 7, //integer types
	FLOAT = 8, DOUBLE = 9, //floating point types
	POINTER = 10,
	LABEL = VOID; //jump label (only used in compilers)
	/** special type arrays */
	public static final int[] EMPTY = {}, WILDCARD = {UNKNOWN};
	public static final int[] COMPARABLE_TYPES = {TYPE, VOID, BOOL, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, POINTER, -1};
	public static final int[] ORDERED_TYPES = {TYPE, VOID, BOOL, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, POINTER};
	public static final int[] LOGIC_TYPES = {BOOL, BYTE, SHORT, INT, LONG};
	public static final int[] INTEGER_TYPES = {BYTE, SHORT, INT, LONG};
	public static final int[] NUMBER_TYPES = {BYTE, SHORT, INT, LONG, FLOAT, DOUBLE};
	/** pointer flags */
	public static final byte HEAP = 0, STACK = 1, READONLY = 2, FUNCTION = 4;
	private static final Type WILDCARD_PTR = new Type(HEAP, EMPTY, WILDCARD);
	private static final Type VOID_FUNC = new Type(FUNCTION, EMPTY, EMPTY);

	private static IntLink FREE_PTR, FREE_FUN;
	private static final ArrayList<Type> POINTERS = new ArrayList<>(), FUNCTIONS = new ArrayList<>();
	private static final HashMap<Type, Integer> INDEX = new HashMap<>();
	static {clearTypeIndex();}

	/** clears all internal id mappings for pointer types */
	public static void clearTypeIndex() {
		FREE_PTR = null;
		FREE_FUN = null;
		POINTERS.clear();
		FUNCTIONS.clear();
		INDEX.clear();
		WILDCARD_PTR.define(0);
		VOID_FUNC.define(0);
	}

	/**@param id of type
	 * @return type declaration or null if not a function or pointer type */
	public static Type type(int id) {
		if (id < 0) return FUNCTIONS.get(~id);
		if (id >= POINTER) return POINTERS.get(id - POINTER);
		return null;
	}

	/**@param function = true for function type, false for pointer type
	 * @return a newly allocated type id for {@link #define(int)} */
	public static int newId(boolean function) {
		if (function) {
			IntLink free = FREE_FUN;
			if (free != null) {
				FREE_FUN = free.next;
				return free.val;
			}
			FUNCTIONS.add(VOID_FUNC);
			return -FUNCTIONS.size();
		} else {
			IntLink free = FREE_PTR;
			if (free != null) {
				FREE_PTR = free.next;
				return free.val;
			}
			POINTERS.add(WILDCARD_PTR);
			return POINTERS.size() + POINTER - 1;
		}
	}

	/**@param s type descriptor string
	 * @return the array of type ids described by s
	 * @throws IllegalArgumentException if s is not a valid type descriptor */
	public static int[] parseTypes(CharSequence s) {
		CharBuffer buf = CharBuffer.wrap(s);
		int[] arr = new int[buf.limit()];
		int l; try {
			l = parse(arr, 0, buf);
		} catch(BufferOverflowException e) {
			throw new IllegalArgumentException("unexpected end of string", e);
		}
		if (buf.hasRemaining()) throw new IllegalArgumentException(format(
			"invalid char '%c' @%d", buf.get(), buf.position() - 1
		));
		return l == arr.length ? arr : Arrays.copyOf(arr, l);
	}

	private static int parse(int[] arr, int i, CharBuffer s) {
		while(s.hasRemaining()) {
			byte flags = FUNCTION;
			char c = s.get();
			switch(c) {
			default: s.position(s.position() - 1); return i;
			case '?': arr[i++] = UNKNOWN; break;
			case 'T': arr[i++] = TYPE; break;
			case 'V': arr[i++] = VOID; break;
			case 'Z': arr[i++] = BOOL; break;
			case 'B': arr[i++] = BYTE; break;
			case 'S': arr[i++] = SHORT; break;
			case 'I': arr[i++] = INT; break;
			case 'L': arr[i++] = LONG; break;
			case 'F': arr[i++] = FLOAT; break;
			case 'D': arr[i++] = DOUBLE; break;
			case '[':
				flags = HEAP;
				if ((c = s.get()) == '!') {
					flags |= STACK;
					c = s.get();
				}
				if (c == '#') flags |= READONLY;
				else s.position(s.position() - 1);
			case '(':
				int j = parse(arr, i, s), k = j;
				if ((c = s.get()) == ':') {
					k = parse(arr, j, s);
					c = s.get();
				}
				char exp = (flags & FUNCTION) != 0 ? ')' : ']';
				if (c != exp) throw new IllegalArgumentException(format(
					"expected '%c' @%d, got '%c'", exp, s.position() - 1, c
				));
				arr[i] = new Type(flags,
					copyOfRange(arr, i, j),
					copyOfRange(arr, j, k)
				).define(0);
				i++;
			}
		}
		return i;
	}

	/**@param types
	 * @return a string representation of types (pointer types are not resolved) */
	public static String name(int... types) {
		return name(new StringBuilder(), 0, types).toString();
	}

	/**@param sb StringBuilder to write into
	 * @param depth how deep pointer and function types should be resolved
	 * @param types the array of types to serialize into sb
	 * @return sb */
	public static StringBuilder name(StringBuilder sb, int depth, int... types) {
		for (int t : types)
			if (t < 0) {
				sb.append('(');
				if (depth > 0) {
					Type ft = type(t);
					name(sb, depth - 1, ft.par).append(':');
					name(sb, depth - 1, ft.ret);
				}
				sb.append(')');
			} else if (t >= POINTER) {
				sb.append('[');
				if (depth > 0) {
					Type pt = type(t);
					if (pt.stackAlloc()) sb.append('!');
					if (pt.readOnly()) sb.append('#');
					name(sb, depth - 1, pt.par);
					if (pt.ret.length > 0)
						name(sb.append(':'), depth - 1, pt.ret);
				}
				sb.append(']');
			} else sb.append(names.charAt(t));
		return sb;
	}

	/**@param from source types of assignment
	 * @param to destination types of assignment
	 * @return whether the given assignment is legal */
	public static boolean canAssign(int[] from, int[] to) {
		int l = from.length;
		if (l != to.length) return false;
		for (int i = 0; i < l; i++)
			if (!canAssign(from[i], to[i])) return false;
		return true;
	}

	/**@param from source type of assignment
	 * @param to destination type of assignment
	 * @return whether the given assignment is legal */
	public static boolean canAssign(int from, int to) {
		if (from == to) return true;
		Type src = type(from), dst = type(to);
		if (src == null || dst == null
			|| (src.flags & ~dst.flags | (src.flags ^ dst.flags) & FUNCTION) != 0
			|| (src.visiting ^ dst.visiting)
		) return false;
		if (!src.visiting) try {
			src.visiting = dst.visiting = true;
			if (src.function())
				return canAssign(src.par, dst.par) && canAssign(dst.ret, src.ret);
			if (dst.ret.length != 1 || dst.ret[0] != UNKNOWN)
				return canAssign(src.par, dst.par) && canAssign(src.ret, dst.ret);
			int l = dst.par.length;
			if (l > src.par.length) return false;
			for (int i = 0; i < l; i++)
				if (!canAssign(src.par[i], dst.par[i])) return false;
		} finally {
			src.visiting = dst.visiting = false;
		}
		return true;
	}

	public static int union(int a, int b) {
		if (a == b) return a;
		Type ta = type(a), tb = type(b);
		if (ta == null || tb == null || ((ta.flags ^ tb.flags) & FUNCTION) != 0)
			return UNKNOWN;
		if (ta.function()) {
			if (canAssign(b, a)) return a;
			if (canAssign(a, b)) return b;
			return UNKNOWN;
		}
		if (ta.visiting ^ tb.visiting) return POINTER;
		if (!ta.visiting) try {
			ta.visiting = tb.visiting = true;
			int l = min(ta.par.length, tb.par.length);
			int[] par = new int[l], ret = WILDCARD;
			for (int i = 0; i < l; i++) {
				int t = union(ta.par[i], tb.par[i]);
				if (t == UNKNOWN) l = i;
				else par[i] = t;
			}
			if (l == ta.par.length && l == tb.par.length && ta.ret.length == tb.ret.length) {
				ret = new int[ta.ret.length];
				for (int i = 0; i < ret.length; i++) {
					int t = union(ta.ret[i], tb.ret[i]);
					if (t == UNKNOWN) {
						ret = WILDCARD;
						break;
					} else ret[i] = t;
				}
			}
			return new Type(
				(byte)(ta.flags | tb.flags),
				l == par.length ? par : Arrays.copyOf(par, l),
				ret
			).define(0);
		} finally {
			ta.visiting = tb.visiting = false;
		}
		return a;
	}

	//Implementation of pointer types:
	/** pointer flags: {@link #HEAP} | {@link #STACK}
	 * | {@link #READONLY} | {@link #FUNCTION} | {@link #RECURSIVE} */
	public final byte flags;
	/** parameter and return types of a function or structure and array types of a pointer */
	public final int[] par, ret;
	/** set during recursive traversal to detect loops */
	private boolean visiting;
	/** target representation (set by compiler) */
	public String name;

	/**Create a new data or function pointer type definition.
	 * @param flags see {@link #flags}
	 * @param par function parameter types or pointer structure types
	 * @param ret function return types or pointer array types */
	public Type(byte flags, int[] par, int[] ret) {
		this.flags = flags;
		this.par = par;
		this.ret = ret;
	}

	@Override
	public int hashCode() {
		int hash = 31 * flags + Arrays.hashCode(par);
		return 31 * hash + Arrays.hashCode(ret);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof Type)) return false;
		Type p = (Type)obj;
		return p.flags == flags
			&& Arrays.equals(p.par, par)
			&& Arrays.equals(p.ret, ret);
	}

	public boolean function() {
		return (flags & FUNCTION) != 0;
	}

	public boolean stackAlloc() {
		return (flags & STACK) != 0;
	}

	public boolean readOnly() {
		return (flags & READONLY) != 0;
	}

	/**Define this type or obtain the id of an existing definition.
	 * @param id to register this type as or 0 to auto assign a unique id.
	 * @return the given id if the type wasn't already registered, otherwise the existing id.
	 * @see #newId(boolean) */
	public int define(int id) {
		if (id == 0) id = newId(function());
		Integer i = INDEX.putIfAbsent(this, id);
		if (i == null) {
			if (id < 0) FUNCTIONS.set(~id, this);
			else POINTERS.set(id - POINTER, this);
			return id;
		}
		if (id < 0) FREE_FUN = new IntLink(FREE_FUN, id);
		else FREE_PTR = new IntLink(FREE_PTR, id);
		return i;
	}

}
