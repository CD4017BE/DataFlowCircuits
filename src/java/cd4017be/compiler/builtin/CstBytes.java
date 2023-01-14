package cd4017be.compiler.builtin;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;

import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class CstBytes extends Value {

	public static final Type CST_BYTES = Type.builtin("cbytes");
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

	public static Value bcast(Arguments args, ScopeData scope) {
		Value vb = args.in(1);
		if (vb instanceof CstBytes cb)
			return vb;
		if (vb instanceof CstInt cb)
			return new CstBytes(cb.value);
		if (vb instanceof CstFloat cb)
			return new CstBytes(Double.doubleToLongBits(cb.value));
		return null;
	}

	public static Value cast(Arguments args, ScopeData scope) {
		Value vb = args.in(1);
		if (vb instanceof CstBytes cb)
			return vb;
		if (vb instanceof CstInt cb)
			return new CstBytes(cb.toString());
		if (vb instanceof CstFloat cb)
			return new CstBytes(cb.toString());
		return null;
	}

	public static Value con(Arguments args, ScopeData scope) {
		CstBytes ca = (CstBytes)args.in(0);
		Value vb = args.in(1);
		if (vb instanceof CstBytes cb) {
			if (ca.len == 0) return cb;
			if (cb.len == 0) return ca;
			byte[] r = new byte[ca.len + cb.len];
			System.arraycopy(ca.value, ca.ofs, r, 0, ca.len);
			System.arraycopy(cb.value, cb.ofs, r, ca.len, cb.len);
			return new CstBytes(r);
		}
		return null;
	}

	public static Value get(Arguments args, ScopeData scope) throws SignalError {
		CstBytes ca = (CstBytes)args.in(0);
		Value vb = args.in(1);
		if (args.ins() > 2) {
			Value vc = args.in(2);
			if (vb instanceof CstInt cb && vc instanceof CstInt cc) {
				int l = ca.len;
				int idx0 = (int)cb.value, idx1 = (int)cc.value;
				if (idx0 < 0 && (idx0 += l) < 0) idx0 = 0;
				else if (idx0 > l) idx0 = l;
				if (idx1 < 0 && (idx1 += l) < 0) idx1 = 0;
				else if (idx1 > ca.len) idx1 = l;
				if (idx0 == 0 && idx1 == l)
					return ca;
				if (idx0 >= idx1)
					return EMPTY;
				return new CstBytes(ca.value, ca.ofs + idx0, idx1 - idx0);
			}
		} else if (vb instanceof CstInt cb) {
			int idx = (int)cb.value;
			if (idx == 0 || idx < 0 && (idx += ca.len) < 0)
				return ca;
			else if (idx >= ca.len)
				return EMPTY;
			return new CstBytes(ca.value, ca.ofs + idx, ca.len - idx);
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
			return new CstInt(pos);
		}
		return args.error("invalid index type");
	}

	public static Value len(Arguments args, ScopeData scope) {
		CstBytes ca = (CstBytes)args.in(0);
		return new CstInt(ca.len);
	}

	public static Value cmp(Arguments args, ScopeData scope) {
		CstBytes ca = (CstBytes)args.in(0);
		Value vb = args.in(1);
		if (vb instanceof CstBytes cb)
			return new CstInt(Arrays.compare(
				ca.value, ca.ofs, ca.ofs + ca.len,
				cb.value, cb.ofs, cb.ofs + cb.len
			));
		return null;
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		return new CstBytes(data);
	}
}