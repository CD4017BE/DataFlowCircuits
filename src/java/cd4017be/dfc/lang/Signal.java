package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.GlobalVar.GLOBALS;
import static cd4017be.dfc.lang.type.Primitive.WORD;
import static cd4017be.dfc.lang.type.Primitive.UWORD;
import static cd4017be.dfc.lang.type.Types.VOID;
import static java.lang.Double.*;
import static java.lang.Float.*;

import java.util.ArrayList;

import cd4017be.dfc.lang.type.*;
import cd4017be.dfc.lang.type.Type;

/**Stack data type.
 * @author CD4017BE */
public class Signal {

	/**state ids */
	public static final int IMAGE = 0, CONST = 1, VAR = 2;
	public static final Signal NULL = new Signal(VOID, IMAGE, 0L);
	private static final ArrayList<long[]> CONSTANTS = new ArrayList<>();

	public final Type type;
	public final int state;
	public long value;

	public static Signal global(Type type, Node node, String name) {
		new GlobalVar(node, name);
		return new Signal(type, CONST, GLOBALS.size());
	}

	public static Signal cst(Type type, long[] value) {
		for (long v : value)
			if (v != 0L) {
				CONSTANTS.add(value);
				return new Signal(type, CONST, CONSTANTS.size());
			}
		return new Signal(type, CONST, 0L);
	}

	public static Signal bundle(Signal... source) {
		if (source.length == 0) return NULL;
		if (source.length == 1) return source[0];
		int state = IMAGE, count = 0;
		for (Signal s : source) {
			state = Math.max(state, s.state);
			if (s.type instanceof Bundle) count += s.value;
			else count++;
		}
		return new Signal(new Bundle(source), state, count);
	}

	public static Signal cst(boolean value) {
		return new Signal(Primitive.BOOL, CONST, value ? 1L : 0L);
	}

	public static Signal cst(double value) {
		return new Signal(Primitive.DOUBLE, CONST, doubleToRawLongBits(value));
	}

	public static Signal cst(float value) {
		return new Signal(Primitive.FLOAT, CONST, floatToRawIntBits(value));
	}

	public static Signal cst(Primitive type, long value) {
		return new Signal(type, CONST, castPrim(type, value));
	}

	public static long castPrim(Primitive type, long value) {
		int sh = 64 - type.bits;
		value <<= sh;
		return type.signed ? value >> sh : value >>> sh;
	}

	public static Signal var(Type type) {
		return new Signal(type, VAR, -1);
	}

	public static Signal img(Type type) {
		return new Signal(type, IMAGE, 0);
	}

	public Signal(Type type, int state, long value) {
		this.type = type;
		this.state = state;
		this.value = value;
	}

	public boolean hasValue() {
		return state != IMAGE;
	}

	public boolean isConst() {
		return state == CONST;
	}

	public boolean isVar() {
		return state == VAR;
	}

	public float asFloat() {
		return intBitsToFloat((int)value);
	}

	public double asDouble() {
		return longBitsToDouble(value);
	}

	public boolean asBool() {
		return value != 0;
	}

	public long getIndex(int i) {
		if (value == 0) return 0L;
		return CONSTANTS.get((int)value - 1)[i];
	}

	public Signal getElement(int i) {
		Bundle b = (Bundle)type;
		if (value == b.source.length)
			return b.source[i];
		for (Signal s : b.source)
			if (s.type instanceof Bundle) {
				if (i < s.value)
					return s.getElement(i);
				i -= s.value;
			} else if (i == 0) return s;
			else i--;
		throw new IndexOutOfBoundsException(i);
	}

	public Signal[] asBundle() {
		if (type == VOID) return new Signal[0];
		if (!(type instanceof Bundle)) return new Signal[] {this};
		Bundle b = (Bundle)type;
		if (b.source.length == value) return b.source;
		Signal[] arr = new Signal[(int)value];
		putBundle(arr, 0, b);
		return arr;
	}

	private int putBundle(Signal[] arr, int i, Bundle b) {
		for (Signal s : b.source)
			if (s.type instanceof Bundle)
				i = putBundle(arr, i, (Bundle)s.type);
			else arr[i++] = s;
		return i;
	}

	private static final char[] hex = "0123456789ABCDEF".toCharArray();

	private static StringBuilder constToString(StringBuilder sb, Type type, long val) {
		if (type instanceof Primitive)
			switch((Primitive)type) {
			case FLOAT: return sb.append(intBitsToFloat((int)val));
			case DOUBLE: return sb.append(longBitsToDouble(val));
			default: return sb.append(val);
			}
		if (type instanceof Pointer || type instanceof Function) {
			if (val <= 0) return sb.append("null");
			return sb.append(GLOBALS.get((int)val - 1));
		}
		if (type instanceof Bundle) {
			//TODO 
			return sb;
		}
		if (val <= 0) return sb.append("zeroinitializer");
		long[] data = CONSTANTS.get((int)val - 1);
		if (type instanceof Vector) {
			Vector vec = (Vector)type;
			if (data.length != vec.count) throw new IllegalStateException();
			type = vec.element;
			if (!vec.simd && (type == WORD || type == UWORD)) {
				sb.append("c\"");
				for (long e : data) {
					char c = (char)(e & 0xff);
					if (c < 32 || c == '"' || c == '\\')
						sb.append('\\').append(hex[c >>> 4 & 15]).append(hex[c & 15]);
					else sb.append(c);
				}
				return sb.append('"');
			}
			sb.append(vec.simd ? '<' : '[');
			String pre = type.canSimd() ? type.toString() + ' ' : "";
			for (long e : data)
				constToString(sb.append(pre), type, e).append(", ");
			return sb.replace(sb.length() - 2, sb.length(), vec.simd ? ">" : "]");
		}
		if (type instanceof Struct) {
			Struct str = (Struct)type;
			if (data.length != str.elements.length) throw new IllegalStateException();
			sb.append('{');
			for (int i = 0; i < data.length; i++) {
				type = str.elements[i];
				if (type.canSimd()) sb.append(type).append(' ');
				constToString(sb, type, data[i]).append(", ");
			}
			return sb.replace(sb.length() - 2, sb.length(), "}");
		}
		throw new IllegalArgumentException();
	}

	@Override
	public String toString() {
		if (state == IMAGE) return "undef";
		if (state == VAR) return "%" + value;
		return constToString(new StringBuilder(), type, value).toString();
	}

//	public static Signal[] union(Signal[] a, Signal[] b, int node) throws SignalError {
//		int l = a.length;
//		if (l != b.length)
//			throw new SignalError(node, -1, "different signal sizes");
//		Signal[] s = new Signal[l];
//		for (int i = 0; i < l; i++)
//			s[i] = union(a[i], b[i], node);
//		return s;
//	}

//	private static Signal union(Signal a, Signal b, int node) throws SignalError {
//		int t = a.type;
//		if (t == b.type && a.state == CONST && b.state == CONST) {
//			if (a.addr == b.addr) return a;
//			if (t == TYPE) return new Signal(
//				t, Type.union((int)a.addr, (int)b.addr)
//			);
//		} else if ((t = Type.union(t, b.type)) == UNKNOWN)
//			throw new SignalError(node, -1,
//				format("can't combine %s & %s", Type.name(a.type), Type.name(b.type))
//			);
//		return new Signal(t);
//	}

//	public static boolean matches(Signal[] t, int... type) {
//		if (t.length < type.length) return false;
//		for (int i = 0; i < type.length; i++)
//			if (t[i].type != type[i]) return false;
//		return true;
//	}

	public static String name(Signal outType) {
		if (outType == NULL) return "empty";
		return outType.type.toString() + " " + outType.toString();
	}

//	public static Signal[] cstTypes(int[] vals) {
//		Signal[] t = new Signal[vals.length];
//		for (int i = 0; i < vals.length; i++)
//			t[i] = new Signal(TYPE, vals[i]);
//		return t;
//	}
//
//	public static Signal[] cst(int type, long val) {
//		return new Signal[] {new Signal(type, val)};
//	}
//
//	public static Signal[] cst(int type, long[] vals) {
//		Signal[] t = new Signal[vals.length];
//		for (int i = 0; i < vals.length; i++)
//			t[i] = new Signal(type, vals[i]);
//		return t;
//	}
//
//	public static Signal[] var(int... type) {
//		Signal[] nt = new Signal[type.length];
//		for (int i = 0; i < type.length; i++)
//			nt[i] = new Signal(type[i]);
//		return nt;
//	}
//
//	public static Signal[] var(Signal[] type) {
//		Signal[] nt = new Signal[type.length];
//		for (int i = 0; i < type.length; i++) {
//			int t = type[i].type;
//			nt[i] = t == TYPE ? new Signal(t, UNKNOWN) : new Signal(t);
//		}
//		return nt;
//	}
//
//	public static Signal[] vec(Signal[] type, long val) {
//		int l = type.length;
//		if (l == 0) return type;
//		Signal cst = new Signal(type[0].type, val);
//		Signal[] t = new Signal[l];
//		for (int i = 0; i < l; i++) t[i] = cst;
//		return t;
//	}
//
//	public static Signal[] vec(int type, int len) {
//		if (len <= 0) return EMPTY;
//		Signal[] t = new Signal[len];
//		for (int i = 0; i < len; i++)
//			t[i] = new Signal(type);
//		return t;
//	}
//
//	public static int[] types(Signal[] s) {
//		int l = s.length;
//		if (l == 0) return Type.EMPTY;
//		int[] types = new int[l];
//		for (int i = 0; i < l; i++) {
//			int t = s[i].type;
//			types[i] = t == TYPE ? (int)s[i].addr : t;
//		}
//		return types;
//	}
//
//	public static Signal[] parseConstants(String s, int node) throws SignalError {
//		ArrayList<Signal> res = new ArrayList<>();
//		int type = INT;
//		boolean string = false;
//		for (int i = 0, i0 = 0; i < s.length(); i++) {
//			char c = s.charAt(i);
//			if (string) {
//				if (c == (type == BYTE ? '\'' : '"')) {
//					string = false;
//					i0 = i+1;
//					continue;
//				}
//				if (c == '\\') {
//					if (++i >= s.length())
//						throw new SignalError(node, -1, "unexpected end of string");
//					switch(c = s.charAt(i)) {
//					case 'n': c = '\n'; break;
//					case 'r': c = '\r'; break;
//					case 't': c = '\t'; break;
//					case '\'', '\"', '\\': break;
//					default:
//						i0 = i + (type == BYTE ? 2 : 4);
//						if (i0 > s.length())
//							throw new SignalError(node, -1, "unexpected end of string");
//						for (c = 0; i < i0; i++) {
//							int v = Character.digit(s.charAt(i), 16);
//							if (v < 0)
//								throw new SignalError(node, -1, "invalid escape char " + s.charAt(i));
//							c = (char)(c << 4 | v);
//						}
//					}
//				}
//				res.add(new Signal(type, c));
//				continue;
//			}
//			if (i == i0) switch(c) {
//				case '\'': type = BYTE; string = true; continue;
//				case '\"': type = SHORT; string = true; continue;
//				case 'z', 'Z': type = BOOL; i0++; continue;
//				case 'b', 'B': type = BYTE; i0++; continue;
//				case 's', 'S': type = SHORT; i0++; continue;
//				case 'i', 'I': type = INT; i0++; continue;
//				case 'l', 'L': type = LONG; i0++; continue;
//				case 'f', 'F': type = FLOAT; i0++; continue;
//				case 'd', 'D': type = DOUBLE; i0++; continue;
//				case ' ': i0++; continue;
//			}
//			if (c != ',')
//				if (i+1 == s.length()) i++;
//				else continue;
//			Signal v; try {
//				if (type == DOUBLE)
//					v = new Signal(parseDouble(s.substring(i0, i)));
//				else if (type == FLOAT)
//					v = new Signal(parseFloat(s.substring(i0, i)));
//				else if ((c = s.charAt(i0)) == 'x' || c == 'X')
//					v = new Signal(type, parseUnsignedLong(s.substring(i0 + 1, i), 16));
//				else v = new Signal(type, parseLong(s.substring(i0, i)));
//			} catch(NumberFormatException e) {
//				throw new SignalError(node, -1, e.getMessage());
//			}
//			res.add(v);
//			i0 = i+1;
//		}
//		return res.toArray(Signal[]::new);
//	}

}
