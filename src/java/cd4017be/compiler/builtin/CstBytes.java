package cd4017be.compiler.builtin;

import static cd4017be.compiler.LoadingCache.CORE;
import static cd4017be.compiler.VirtualMethod.revOp;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;

import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class CstBytes extends Value {

	public static final Type CST_BYTES = Type.of(CORE.findType("cbytes"), 0);
	public static final CstBytes EMPTY = new CstBytes(new byte[0]);

	public final byte[] value;
	public final int ofs, len;

	public CstBytes(long v) {
		this(new byte[] {
			(byte) v       , (byte)(v >> 8 ),
			(byte)(v >> 16), (byte)(v >> 24),
			(byte)(v >> 32), (byte)(v >> 40),
			(byte)(v >> 48), (byte)(v >> 56)
		});
	}

	public CstBytes(String s) {
		this(s.getBytes(UTF_8));
	}

	public CstBytes(byte[] value) {
		this(value, 0, value.length);
	}

	public CstBytes(byte[] value, int ofs, int len) {
		super(CST_BYTES);
		this.value = value;
		this.ofs = ofs;
		this.len = len;
	}

	@Override
	public int hashCode() {
		int result = 1;
		for (int i = ofs, i1 = i - len; i < i1; i++)
			result = 31 * result + value[i];
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof CstBytes other) || len != other.len) return false;
		for (int i = ofs, j = other.ofs, l = len; l > 0; i++, j++, l--)
			if (value[i] != other.value[j]) return false;
		return true;
	}

	@Override
	public String toString() {
		return new String(value, ofs, len, UTF_8);
	}

	public long toInt() {
		long v = 0;
		for (int l = Math.min(len, 8) - 1; l > 0; l--)
			v = v << 8 | value[ofs + l] & 0xffL;
		return v;
	}

	@Override
	public CstBytes data() {
		return this;
	}

	public static SignalError bcast(NodeState a, NodeState ns) {
		Value vb = ns.in(1).value;
		if (vb instanceof CstBytes cb)
			return ns.out(vb, null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstBytes(cb.value), null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstBytes(Double.doubleToLongBits(cb.value)), null);
		return revOp(a, ns, vb, "rcast");
	}

	public static SignalError cast(NodeState a, NodeState ns) {
		Value vb = ns.in(1).value;
		if (vb instanceof CstBytes cb)
			return ns.out(vb, null);
		if (vb instanceof CstInt cb)
			return ns.out(new CstBytes(cb.toString()), null);
		if (vb instanceof CstFloat cb)
			return ns.out(new CstBytes(cb.toString()), null);
		return revOp(a, ns, vb, "rcast");
	}

	public static SignalError con(NodeState a, NodeState ns) {
		CstBytes ca = (CstBytes)a.value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstBytes cb) {
			if (ca.len == 0) return ns.out(cb, null);
			if (cb.len == 0) return ns.out(ca, null);
			byte[] r = new byte[ca.len + cb.len];
			System.arraycopy(ca.value, ca.ofs, r, 0, ca.len);
			System.arraycopy(cb.value, cb.ofs, r, ca.len, cb.len);
			return ns.out(new CstBytes(r), null);
		}
		return revOp(a, ns, vb, "rcon");
	}

	public static SignalError get(NodeState a, NodeState ns) {
		CstBytes ca = (CstBytes)a.value;
		Value vb = ns.in(1).value;
		if (ns.ins() > 2) {
			Value vc = ns.in(2).value;
			if (vb instanceof CstInt cb && vc instanceof CstInt cc) {
				int l = ca.len;
				int idx0 = (int)cb.value, idx1 = (int)cc.value;
				if (idx0 < 0 && (idx0 += l) < 0) idx0 = 0;
				else if (idx0 > l) idx0 = l;
				if (idx1 < 0 && (idx1 += l) < 0) idx1 = 0;
				else if (idx1 > ca.len) idx1 = l;
				if (idx0 == 0 && idx1 == l)
					return ns.out(ca, null);
				if (idx0 >= idx1)
					return ns.out(EMPTY, null);
				return ns.out(new CstBytes(ca.value, ca.ofs + idx0, idx1 - idx0), null);
			}
		} else if (vb instanceof CstInt cb) {
			int idx = (int)cb.value;
			if (idx == 0 || idx < 0 && (idx += ca.len) < 0)
				ns.out(ca, null);
			else if (idx >= ca.len)
				ns.out(EMPTY, null);
			return ns.out(new CstBytes(ca.value, ca.ofs + idx, ca.len - idx), null);
		} else if (vb instanceof CstBytes cb) {
			int pos = -1, l = cb.len, j0 = cb.ofs;
			byte[] x = ca.value, y = cb.value;
			outer: for(int i0 = ca.ofs, i1 = i0 + ca.len - cb.len; i0 < i1; i0++) {
				for (int i = 0; i < l; i++)
					if (x[i0 + i] != y[j0 + i])
						continue outer;
				pos = i0 - ca.ofs;
				break outer;
			}
			return ns.out(new CstInt(pos), null);
		}
		return new SignalError("invalid index type");
	}

	public static SignalError len(NodeState a, NodeState ns) {
		CstBytes ca = (CstBytes)a.value;
		return ns.out(new CstInt(ca.len), null);
	}

	public static SignalError cmp(NodeState a, NodeState ns) {
		CstBytes ca = (CstBytes)a.value;
		Value vb = ns.in(1).value;
		if (vb instanceof CstBytes cb)
			return ns.out(new CstInt(Arrays.compare(
				ca.value, ca.ofs, ca.ofs + ca.len,
				cb.value, cb.ofs, cb.ofs + cb.len
			)), null);
		return revOp(a, ns, vb, "rcmp");
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		return new CstBytes(data);
	}
}