package cd4017be.compiler.builtin;

import static cd4017be.compiler.LoadingCache.CORE;
import static cd4017be.compiler.VirtualMethod.revOp;
import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Double.longBitsToDouble;

import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class CstFloat extends Value {

	public static final Type CST_FLOAT = new Type(CORE.findType("cfloat"), 0).unique();

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

	public static SignalError bcast(NodeState a, NodeState ns) {
		Value vb = ns.in(1).value;
		if (vb instanceof CstFloat cb)
			return ns.out(vb, null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstFloat(longBitsToDouble(cb.value)), null);
		if (vb instanceof CstBytes cb)
			return ns.out(new CstFloat(longBitsToDouble(cb.toInt())), null);
		return revOp(a, ns, vb, "rcast");
	}

	public static SignalError cast(NodeState a, NodeState ns) {
		Value vb = ns.in(1).value;
		if (vb instanceof CstFloat cb)
			return ns.out(vb, null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstFloat(cb.value), null);
		if (vb instanceof CstBytes cb)
			return ns.out(new CstFloat(cb.toString()), null);
		return revOp(a, ns, vb, "rcast");
	}

	public static SignalError add(NodeState a, NodeState ns) {
		double x = ((CstFloat)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat(x + cb.value), null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstFloat(x + (double)cb.value), null);
		return revOp(a, ns, vb, "radd");
	}

	public static SignalError sub(NodeState a, NodeState ns) {
		double x = ((CstFloat)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat(x - cb.value), null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstFloat(x - (double)cb.value), null);
		return revOp(a, ns, vb, "rsub");
	}

	public static SignalError mul(NodeState a, NodeState ns) {
		double x = ((CstFloat)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat(x * cb.value), null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstFloat(x * (double)cb.value), null);
		return revOp(a, ns, vb, "rmul");
	}

	public static SignalError div(NodeState a, NodeState ns) {
		double x = ((CstFloat)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat(x / cb.value), null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstFloat(x / (double)cb.value), null);
		return revOp(a, ns, vb, "rdiv");
	}

	public static SignalError mod(NodeState a, NodeState ns) {
		double x = ((CstFloat)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat(x % cb.value), null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstFloat(x % (double)cb.value), null);
		return revOp(a, ns, vb, "rmod");
	}

	public static SignalError neg(NodeState a, NodeState ns) {
		double x = ((CstFloat)a.value).value;
		return ns.out(new CstFloat(-x), null);
	}

	public static SignalError cmp(NodeState a, NodeState ns) {
		double x = ((CstFloat)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstFloat cb)
			return ns.out(new CstInt(x < cb.value ? -1 : x > cb.value ? 1 : 0), null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstInt(x < (double)cb.value ? -1 : x > (double)cb.value ? 1 : 0), null);
		return revOp(a, ns, vb, "rcmp");
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		long val = data[0] & 0xffL | (data[1] & 0xffL) << 8
		| (data[2] & 0xffL) << 16 | (data[3] & 0xffL) << 24
		| (data[4] & 0xffL) << 32 | (data[5] & 0xffL) << 40
		| (data[6] & 0xffL) << 48 | (data[7] & 0xffL) << 56;
		return new CstFloat(longBitsToDouble(val));
	}
}