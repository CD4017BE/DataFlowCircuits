package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.type.Primitive.WORD;
import static cd4017be.dfc.lang.type.Primitive.UWORD;
import static cd4017be.dfc.lang.type.Types.VOID;
import static java.lang.Double.*;
import static java.lang.Float.*;

import java.lang.reflect.Array;
import java.util.Objects;

import cd4017be.dfc.graph.Node;
import cd4017be.dfc.lang.type.*;
import cd4017be.dfc.lang.type.Type;

/**Stack data type.
 * @author CD4017BE */
public class Signal {

	/**state ids */
	public static final int IMAGE = 0, CONST = 1, VAR = 2;
	public static final Signal NULL = new Signal(VOID, IMAGE, null);

	public final Type type;
	public final int state;
	public Object value;

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
		return new Signal(type, VAR, null);
	}

	public static Signal img(Type type) {
		return new Signal(type, IMAGE, null);
	}

	public Signal(Type type, int state, Object value) {
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

	public long asLong() {
		return value == null ? 0 : (Long)value;
	}

	public float asFloat() {
		return intBitsToFloat((int)asLong());
	}

	public double asDouble() {
		return longBitsToDouble(asLong());
	}

	public boolean asBool() {
		return (asLong() & 1L) != 0;
	}

	public Object getIndex(int i) {
		return value == null ? null : Array.get(value, i);
	}

	public Signal getElement(int i) {
		return ((Bundle)type).getElement(this, i);
	}

	public Bundle asBundle() {
		if (type == VOID || type instanceof Control) return null;
		if (type instanceof Bundle b) return b;
		return new Bundle(null, this, null);
	}

	public int bundleSize() {
		return type == VOID || type instanceof Control ? 0 : type instanceof Bundle ? (int)value : 1;
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this
			|| obj instanceof Signal s && s.state == state
			&& type.equals(s.type) && Objects.equals(s.value, value);
	}

	private static final char[] hex = "0123456789ABCDEF".toCharArray();

	private static StringBuilder constToString(StringBuilder sb, Type type, Object val) {
		if (type instanceof Primitive)
			switch((Primitive)type) {
			case FLOAT: return sb.append(intBitsToFloat(((Long)val).intValue()));
			case DOUBLE: return sb.append(longBitsToDouble((Long)val));
			default: return sb.append(val);
			}
		if (type instanceof Pointer || type instanceof Function) {
			return val instanceof Node n
				? sb.append('@').append(n.data)
				: sb.append("null");
		}
		if (val == null) return sb.append("zeroinitializer");
		int len = Array.getLength(val);
		if (type instanceof Vector) {
			Vector vec = (Vector)type;
			if (len != vec.count) throw new IllegalStateException();
			type = vec.element;
			if (!vec.simd && (type == WORD || type == UWORD)) {
				sb.append("c\"");
				for (long e : (long[])val) {
					char c = (char)(e & 0xff);
					if (c < 32 || c == '"' || c == '\\')
						sb.append('\\').append(hex[c >>> 4 & 15]).append(hex[c & 15]);
					else sb.append(c);
				}
				return sb.append('"');
			}
			sb.append(vec.simd ? '<' : '[');
			String pre = type.canSimd() ? type.toString() + ' ' : "";
			for (int i = 0; i < len; i++)
				constToString(sb.append(pre), type, Array.get(val, i)).append(", ");
			return sb.replace(sb.length() - 2, sb.length(), vec.simd ? ">" : "]");
		}
		if (type instanceof Struct) {
			Struct str = (Struct)type;
			if (len != str.elements.length) throw new IllegalStateException();
			sb.append('{');
			for (int i = 0; i < len; i++) {
				type = str.elements[i];
				if (type.canSimd()) sb.append(type).append(' ');
				constToString(sb, type, Array.get(val, i)).append(", ");
			}
			return sb.replace(sb.length() - 2, sb.length(), "}");
		}
		throw new IllegalArgumentException();
	}

	@Override
	public String toString() {
		return value != null
			? state == VAR ? "%" + value : constToString(new StringBuilder(), type, value).toString()
			: state == IMAGE ? "undef" : "zeroinitializer";
	}

	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		type.displayString(sb, nest);
		if (state == IMAGE || state == VAR && type instanceof Bundle)
			return sb;
		sb.append(" = ");
		if (state == VAR)
			return value == null ? sb.append("%?")
				: sb.append('%').append(value);
		return constToString(sb, type, value);
	}

	public static String name(Signal outType) {
		if (outType == NULL) return "empty";
		
		return outType.type.toString() + " " + outType.toString();
	}

}
