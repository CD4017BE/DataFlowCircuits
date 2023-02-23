package cd4017be.compiler.builtin;

import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class CstInt extends Value {

	public static final Type CST_INT = Type.builtin("cint");
	public static final CstInt FALSE = new CstInt(0), TRUE = new CstInt(-1);

	public final long value;

	public CstInt(String s) {
		this(s.startsWith("0x") || s.startsWith("0X")
			? Long.parseUnsignedLong(s.substring(2), 16)
			: Long.parseLong(s)
		);
	}

	public CstInt(long value) {
		super(CST_INT);
		this.value = value;
	}

	@Override
	public String toString() {
		return Long.toString(value);
	}

	@Override
	public int hashCode() {
		return Long.hashCode(value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof CstInt other && value == other.value;
	}

	@Override
	public CstBytes data() {
		return new CstBytes(value);
	}

	public static Value bcast(Arguments args, ScopeData scope) {
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return vb;
		if (vb instanceof CstFloat cb)
			return new CstInt(Double.doubleToRawLongBits(cb.value));
		if (vb instanceof CstBytes cb)
			return new CstInt(cb.toInt());
		return null;
	}

	public static Value cast(Arguments args, ScopeData scope) {
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return vb;
		if (vb instanceof CstFloat cb)
			return new CstInt((long)cb.value);
		if (vb instanceof CstBytes cb)
			return new CstInt(cb.string());
		return null;
	}

	public static Value add(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(x + cb.value);
		if (vb instanceof CstFloat cb)
			return new CstFloat((double)x + cb.value);
		return null;
	}

	public static Value sub(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(x - cb.value);
		if (vb instanceof CstFloat cb)
			return new CstFloat((double)x - cb.value);
		return null;
	}

	public static Value mul(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(x * cb.value);
		if (vb instanceof CstFloat cb)
			return new CstFloat((double)x * cb.value);
		return null;
	}

	public static Value div(Arguments args, ScopeData scope) throws SignalError {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return cb.value == 0L ? args.error("div by 0")
				: new CstInt(x / cb.value);
		if (vb instanceof CstFloat cb)
			return new CstFloat((double)x / cb.value);
		return null;
	}

	public static Value mod(Arguments args, ScopeData scope) throws SignalError {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return cb.value == 0L ? args.error("div by 0")
				: new CstInt(x % cb.value);
		if (vb instanceof CstFloat cb)
			return new CstFloat((double)x % cb.value);
		return null;
	}

	public static Value min(Arguments args, ScopeData scope) throws SignalError {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(x <= cb.value ? x : cb.value);
		if (vb instanceof CstFloat cb)
			return new CstFloat((double)x <= cb.value ? (double)x : cb.value);
		return null;
	}

	public static Value max(Arguments args, ScopeData scope) throws SignalError {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(x >= cb.value ? x : cb.value);
		if (vb instanceof CstFloat cb)
			return new CstFloat((double)x >= cb.value ? (double)x : cb.value);
		return null;
	}

	public static Value mix(Arguments args, ScopeData scope) throws SignalError {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1), vc = args.in(2);
		if (vb instanceof CstInt cb && vc instanceof CstInt cc)
			return new CstInt(x & cc.value | ~x & cb.value);
		return null;
	}

	public static Value neg(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		return new CstInt(-x);
	}

	public static Value abs(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		return new CstInt(x < 0 ? -x : x);
	}

	public static Value sign(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		return new CstInt(x < 0 ? -1 : x > 0 ? 1 : 0);
	}

	public static Value and(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(x & cb.value);
		return null;
	}

	public static Value or(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(x | cb.value);
		return null;
	}

	public static Value xor(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(x ^ cb.value);
		return null;
	}

	public static Value not(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		return new CstInt(~x);
	}

	public static Value any(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		return new CstInt(x != 0 ? -1 : 0);
	}

	public static Value all(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		return new CstInt(x == -1 ? -1 : 0);
	}

	public static Value eq(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vy = args.in(1);
		if (vy instanceof CstInt cy)
			return x == cy.value ? TRUE : FALSE;
		if (vy instanceof CstFloat cy)
			return (double)x == cy.value ? TRUE : FALSE;
		return null;
	}

	public static Value ne(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vy = args.in(1);
		if (vy instanceof CstInt cy)
			return x != cy.value ? TRUE : FALSE;
		if (vy instanceof CstFloat cy)
			return (double)x != cy.value ? TRUE : FALSE;
		return null;
	}

	public static Value lt(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vy = args.in(1);
		if (vy instanceof CstInt cy)
			return x < cy.value ? TRUE : FALSE;
		if (vy instanceof CstFloat cy)
			return (double)x < cy.value ? TRUE : FALSE;
		return null;
	}

	public static Value le(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vy = args.in(1);
		if (vy instanceof CstInt cy)
			return x <= cy.value ? TRUE : FALSE;
		if (vy instanceof CstFloat cy)
			return (double)x <= cy.value ? TRUE : FALSE;
		return null;
	}

	public static Value cmp(Arguments args, ScopeData scope) {
		long x = ((CstInt)args.in(0)).value;
		Value vb = args.in(1);
		if (vb instanceof CstInt cb)
			return new CstInt(Long.compare(x, cb.value));
		if (vb instanceof CstFloat cb)
			return new CstInt((double)x < cb.value ? -1 : (double)x > cb.value ? 1 : 0);
		return null;
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		long val = data[0] & 0xffL | (data[1] & 0xffL) << 8
		| (data[2] & 0xffL) << 16 | (data[3] & 0xffL) << 24
		| (data[4] & 0xffL) << 32 | (data[5] & 0xffL) << 40
		| (data[6] & 0xffL) << 48 | (data[7] & 0xffL) << 56;
		return new CstInt(val);
	}
}