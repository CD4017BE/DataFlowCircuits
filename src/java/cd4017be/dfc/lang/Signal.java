package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.GlobalVar.GLOBALS;
import static cd4017be.dfc.lang.Type.*;
import static cd4017be.dfc.lang.Type.TYPE;
import static java.lang.Double.*;
import static java.lang.Float.*;
import static java.lang.Long.parseLong;
import static java.lang.Long.parseUnsignedLong;
import static java.lang.String.format;

import java.util.ArrayList;

/**Stack data type.
 * @author CD4017BE */
public class Signal {

	public int type;
	public byte state;
	public long addr;

	public Signal(int type, byte state) {
		this.type = type;
		this.state = state;
	}

	public Signal(int type) {this(type, VAR);}

	public Signal(int type, long val) {
		this(type, CONST);
		this.addr = type >= 0 && type < TYPE_MASK.length ? val & TYPE_MASK[type] : val;
	}

	public Signal(double val) {this(DOUBLE, doubleToRawLongBits(val));}
	public Signal(float val) {this(FLOAT, floatToRawIntBits(val));}

	public boolean constant() {
		return state == CONST;
	}

	public boolean defined() {
		return state == CONST || state == DEF;
	}

	public boolean name() {
		if (state == VAR) return false;
		if (state == NAMED) state = DEF;
		return true;
	}

	public void define(long addr) {
		this.addr = addr;
		this.state = DEF;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Signal)) return false;
		return ((Signal)obj).type == type;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		Type.name(sb, 1, type).append(states.charAt(state & 3));
		if (constant()) {
			switch(type) {
			case UNKNOWN -> sb.append("unknown");
			case TYPE -> Type.name(sb, 1, (int)addr);
			case FLOAT -> sb.append(intBitsToFloat((int)addr));
			case DOUBLE -> sb.append(longBitsToDouble(addr));
			case VOID, BOOL, BYTE, SHORT, INT, LONG -> sb.append(addr);
			default -> sb.append(addr == 0L ? "null" : GLOBALS.get((int)(addr - 1)));
			}
		} else if (defined()) sb.append(addr);
		return sb.toString();
	}

	public Signal copy(int n) {
		Signal t = new Signal(type, state);
		t.addr = addr;
		return t;
	}

	public int asType() {
		return type == TYPE ? (int)addr : type;
	}

	public static Signal[] union(Signal[] a, Signal[] b, int node) throws SignalError {
		int l = a.length;
		if (l != b.length)
			throw new SignalError(node, -1, "different signal sizes");
		Signal[] s = new Signal[l];
		for (int i = 0; i < l; i++)
			s[i] = union(a[i], b[i], node);
		return s;
	}

	private static Signal union(Signal a, Signal b, int node) throws SignalError {
		int t = a.type;
		if (t == b.type && a.state == CONST && b.state == CONST) {
			if (a.addr == b.addr) return a;
			if (t == TYPE) return new Signal(
				t, Type.union((int)a.addr, (int)b.addr)
			);
		} else if ((t = Type.union(t, b.type)) == UNKNOWN)
			throw new SignalError(node, -1,
				format("can't combine %s & %s", Type.name(a.type), Type.name(b.type))
			);
		return new Signal(t);
	}

	public static boolean matches(Signal[] t, int... type) {
		if (t.length < type.length) return false;
		for (int i = 0; i < type.length; i++)
			if (t[i].type != type[i]) return false;
		return true;
	}

	public static String name(Signal[] sig) {
		if (sig.length == 0) return "empty";
		if (sig.length == 1) return sig[0].toString();
		StringBuilder sb = new StringBuilder(sig[0].toString());
		for (int i = 1; i < sig.length; i++)
			sb.append(' ').append(sig[i]);
		return sb.toString();
	}

	private static long[] TYPE_MASK = {
		-1L, -1L, 0L, 1L, 0xffL, 0xffffL, 0xffffffffL, -1L, 0xffffffffL, -1L
	};
	private static final String states = ".%=^";

	/**state ids */
	public static final byte VAR = 0, DEF = 1, CONST = 2, NAMED = 3;

	public static final Signal[] EMPTY = {};
	/** Represents unused branches of the program graph that will never execute. */
	public static final Signal[] DEAD_CODE = {new Signal(UNKNOWN, 0)};

	public static Signal[] cstTypes(int[] vals) {
		Signal[] t = new Signal[vals.length];
		for (int i = 0; i < vals.length; i++)
			t[i] = new Signal(TYPE, vals[i]);
		return t;
	}

	public static Signal[] cst(int type, long val) {
		return new Signal[] {new Signal(type, val)};
	}

	public static Signal[] cst(int type, long[] vals) {
		Signal[] t = new Signal[vals.length];
		for (int i = 0; i < vals.length; i++)
			t[i] = new Signal(type, vals[i]);
		return t;
	}

	public static Signal[] var(int... type) {
		Signal[] nt = new Signal[type.length];
		for (int i = 0; i < type.length; i++)
			nt[i] = new Signal(type[i]);
		return nt;
	}

	public static Signal[] var(Signal[] type) {
		Signal[] nt = new Signal[type.length];
		for (int i = 0; i < type.length; i++) {
			int t = type[i].type;
			nt[i] = t == TYPE ? new Signal(t, UNKNOWN) : new Signal(t);
		}
		return nt;
	}

	public static Signal[] vec(Signal[] type, long val) {
		int l = type.length;
		if (l == 0) return type;
		Signal cst = new Signal(type[0].type, val);
		Signal[] t = new Signal[l];
		for (int i = 0; i < l; i++) t[i] = cst;
		return t;
	}

	public static Signal[] vec(int type, int len) {
		if (len <= 0) return EMPTY;
		Signal[] t = new Signal[len];
		for (int i = 0; i < len; i++)
			t[i] = new Signal(type);
		return t;
	}

	public static int[] types(Signal[] s) {
		int l = s.length;
		if (l == 0) return Type.EMPTY;
		int[] types = new int[l];
		for (int i = 0; i < l; i++) {
			int t = s[i].type;
			types[i] = t == TYPE ? (int)s[i].addr : t;
		}
		return types;
	}

	public static Signal[] parseConstants(String s, int node) throws SignalError {
		ArrayList<Signal> res = new ArrayList<>();
		int type = INT;
		boolean string = false;
		for (int i = 0, i0 = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (string) {
				if (c == (type == BYTE ? '\'' : '"')) {
					string = false;
					i0 = i+1;
					continue;
				}
				if (c == '\\') {
					if (++i >= s.length())
						throw new SignalError(node, -1, "unexpected end of string");
					switch(c = s.charAt(i)) {
					case 'n': c = '\n'; break;
					case 'r': c = '\r'; break;
					case 't': c = '\t'; break;
					case '\'', '\"', '\\': break;
					default:
						i0 = i + (type == BYTE ? 2 : 4);
						if (i0 > s.length())
							throw new SignalError(node, -1, "unexpected end of string");
						for (c = 0; i < i0; i++) {
							int v = Character.digit(s.charAt(i), 16);
							if (v < 0)
								throw new SignalError(node, -1, "invalid escape char " + s.charAt(i));
							c = (char)(c << 4 | v);
						}
					}
				}
				res.add(new Signal(type, c));
				continue;
			}
			if (i == i0) switch(c) {
				case '\'': type = BYTE; string = true; continue;
				case '\"': type = SHORT; string = true; continue;
				case 'z', 'Z': type = BOOL; i0++; continue;
				case 'b', 'B': type = BYTE; i0++; continue;
				case 's', 'S': type = SHORT; i0++; continue;
				case 'i', 'I': type = INT; i0++; continue;
				case 'l', 'L': type = LONG; i0++; continue;
				case 'f', 'F': type = FLOAT; i0++; continue;
				case 'd', 'D': type = DOUBLE; i0++; continue;
				case ' ': i0++; continue;
			}
			if (c != ',')
				if (i+1 == s.length()) i++;
				else continue;
			Signal v; try {
				if (type == DOUBLE)
					v = new Signal(parseDouble(s.substring(i0, i)));
				else if (type == FLOAT)
					v = new Signal(parseFloat(s.substring(i0, i)));
				else if ((c = s.charAt(i0)) == 'x' || c == 'X')
					v = new Signal(type, parseUnsignedLong(s.substring(i0 + 1, i), 16));
				else v = new Signal(type, parseLong(s.substring(i0, i)));
			} catch(NumberFormatException e) {
				throw new SignalError(node, -1, e.getMessage());
			}
			res.add(v);
			i0 = i+1;
		}
		return res.toArray(Signal[]::new);
	}

}
