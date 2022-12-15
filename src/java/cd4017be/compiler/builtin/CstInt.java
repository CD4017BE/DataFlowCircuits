package cd4017be.compiler.builtin;

import static cd4017be.compiler.LoadingCache.CORE;
import static cd4017be.compiler.VirtualMethod.revOp;
import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class CstInt extends Value {

	public static final Type CST_INT = Type.of(CORE.findType("cint"), 0);

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

	public static SignalError bcast(NodeState a, NodeState ns) {
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(vb, null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstInt(Double.doubleToRawLongBits(cb.value)), null);
		if (vb instanceof CstBytes cb)
			return ns.out(new CstInt(cb.toInt()), null);
		return revOp(a, ns, vb, "rcast");
	}

	public static SignalError cast(NodeState a, NodeState ns) {
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(vb, null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstInt((long)cb.value), null);
		if (vb instanceof CstBytes cb)
			return ns.out(new CstInt(cb.toString()), null);
		return revOp(a, ns, vb, "rcast");
	}

	public static SignalError add(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(new CstInt(x + cb.value), null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat((double)x + cb.value), null);
		return revOp(a, ns, vb, "radd");
	}

	public static SignalError sub(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(new CstInt(x - cb.value), null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat((double)x - cb.value), null);
		return revOp(a, ns, vb, "rsub");
	}

	public static SignalError mul(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(new CstInt(x * cb.value), null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat((double)x * cb.value), null);
		return revOp(a, ns, vb, "rmul");
	}

	public static SignalError div(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return cb.value == 0L ? new SignalError("div by 0")
				: ns.out(new CstInt(x / cb.value), null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat((double)x / cb.value), null);
		return revOp(a, ns, vb, "rdiv");
	}

	public static SignalError mod(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return cb.value == 0L ? new SignalError("div by 0")
				: ns.out(new CstInt(x % cb.value), null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstFloat((double)x % cb.value), null);
		return revOp(a, ns, vb, "rmod");
	}

	public static SignalError neg(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		return ns.out(new CstInt(-x), null);
	}

	public static SignalError and(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(new CstInt(x & cb.value), null);
		return revOp(a, ns, vb, "rand");
	}

	public static SignalError or(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(new CstInt(x | cb.value), null);
		return revOp(a, ns, vb, "ror");
	}

	public static SignalError xor(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(new CstInt(x ^ cb.value), null);
		return revOp(a, ns, vb, "rxor");
	}

	public static SignalError not(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		return ns.out(new CstInt(~x), null);
	}

	public static SignalError cmp(NodeState a, NodeState ns) {
		long x = ((CstInt)a.value).value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstInt cb)
			return ns.out(new CstInt(Long.compare(x, cb.value)), null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstInt((double)x < cb.value ? -1 : (double)x > cb.value ? 1 : 0), null);
		return revOp(a, ns, vb, "rcmp");
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		long val = data[0] & 0xffL | (data[1] & 0xffL) << 8
		| (data[2] & 0xffL) << 16 | (data[3] & 0xffL) << 24
		| (data[4] & 0xffL) << 32 | (data[5] & 0xffL) << 40
		| (data[6] & 0xffL) << 48 | (data[7] & 0xffL) << 56;
		return new CstInt(val);
	}
}