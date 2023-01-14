package cd4017be.compiler.builtin;

import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Double.longBitsToDouble;

import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class CstFloat extends Value {

	public static final Type CST_FLOAT = Type.builtin("cfloat");

	public final double value;

	public CstFloat(String s) {
		this(Double.parseDouble(s));
	}

	public CstFloat(double value) {
		super(CST_FLOAT);
		this.value = value;
	}

	@Override
	public String toString() {
		return Double.toString(value);
	}

	@Override
	public int hashCode() {
		return Double.hashCode(value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof CstInt other
		&& doubleToRawLongBits(value) == doubleToRawLongBits(other.value);
	}

	@Override
	public CstBytes data() {
		return new CstBytes(doubleToRawLongBits(value));
	}

	public static Value bcast(Arguments args, ScopeData scope) {
		Value vb = args.in(1);
		if (vb instanceof CstFloat cb)
			return vb;
		if (vb instanceof CstInt cb)
			return new CstFloat(longBitsToDouble(cb.value));
		if (vb instanceof CstBytes cb)
			return new CstFloat(longBitsToDouble(cb.toInt()));
		return null;
	}

	public static Value cast(Arguments args, ScopeData scope) {
		Value vb = args.in(1);
		if (vb instanceof CstFloat cb)
			return vb;
		if (vb instanceof CstInt cb)
			return new CstFloat(cb.value);
		if (vb instanceof CstBytes cb)
			return new CstFloat(cb.toString());
		return null;
	}

	public static Value add(Arguments args, ScopeData scope) {
		double x = ((CstFloat)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstFloat cb)
			return new CstFloat(x + cb.value);
		if (vb instanceof CstInt cb)
			return new CstFloat(x + (double)cb.value);
		return null;
	}

	public static Value sub(Arguments args, ScopeData scope) {
		double x = ((CstFloat)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstFloat cb)
			return new CstFloat(x - cb.value);
		if (vb instanceof CstInt cb)
			return new CstFloat(x - (double)cb.value);
		return null;
	}

	public static Value mul(Arguments args, ScopeData scope) {
		double x = ((CstFloat)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstFloat cb)
			return new CstFloat(x * cb.value);
		if (vb instanceof CstInt cb)
			return new CstFloat(x * (double)cb.value);
		return null;
	}

	public static Value div(Arguments args, ScopeData scope) {
		double x = ((CstFloat)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstFloat cb)
			return new CstFloat(x / cb.value);
		if (vb instanceof CstInt cb)
			return new CstFloat(x / (double)cb.value);
		return null;
	}

	public static Value mod(Arguments args, ScopeData scope) {
		double x = ((CstFloat)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstFloat cb)
			return new CstFloat(x % cb.value);
		if (vb instanceof CstInt cb)
			return new CstFloat(x % (double)cb.value);
		return null;
	}

	public static Value neg(Arguments args, ScopeData scope) {
		double x = ((CstFloat)args.in(0)).value;
		return new CstFloat(-x);
	}

	public static Value cmp(Arguments args, ScopeData scope) {
		double x = ((CstFloat)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstFloat cb)
			return new CstInt(x < cb.value ? -1 : x > cb.value ? 1 : 0);
		if (vb instanceof CstInt cb)
			return new CstInt(x < (double)cb.value ? -1 : x > (double)cb.value ? 1 : 0);
		return null;
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		long val = data[0] & 0xffL | (data[1] & 0xffL) << 8
		| (data[2] & 0xffL) << 16 | (data[3] & 0xffL) << 24
		| (data[4] & 0xffL) << 32 | (data[5] & 0xffL) << 40
		| (data[6] & 0xffL) << 48 | (data[7] & 0xffL) << 56;
		return new CstFloat(longBitsToDouble(val));
	}
}