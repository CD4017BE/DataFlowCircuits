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
		Signal s0 = source[0];
		if (source.length == 1) return s0;
		Bundle b = s0.type instanceof Bundle ? (Bundle)s0.type : null;
		int n = source.length + (b == null ? 0 : (int)s0.value - 1);
		for (int i = b == null ? 0 : 1; i < source.length; i++)
			b = new Bundle(b, source[i], null);
		return new Signal(b, VAR, n);
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
		return ((Bundle)type).getElement(this, i);
	}

	public Bundle asBundle() {
		if (type == VOID) return null;
		if (type instanceof Bundle) return (Bundle)type;
		return new Bundle(null, this, null);
	}

	public int bundleSize() {
		return type == VOID ? 0 : type instanceof Bundle ? (int)value : 1;
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

	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		type.displayString(sb, nest);
		if (state == IMAGE || state == VAR && type instanceof Bundle)
			return sb;
		sb.append(" = ");
		if (state == VAR)
			return sb.append('%').append(value);
		return constToString(sb, type, value);
	}

	public static String name(Signal outType) {
		if (outType == NULL) return "empty";
		
		return outType.type.toString() + " " + outType.toString();
	}

}
