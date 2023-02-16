package cd4017be.compiler.builtin;

import java.util.Arrays;
import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class Bundle extends Value {

	public static final Type BUNDLE = Type.builtin("bundle");
	public static final Value[] EMPTY = {};
	public static final Bundle VOID = new Bundle(EMPTY);

	public final Value[] values;

	public Bundle(Value[] values) {
		super(BUNDLE);
		this.values = values;
	}

	public Bundle(Type type, Value[] values) {
		super(type, true);
		this.values = values;
	}

	@Override
	public String toString() {
		if (values.length == 0) return "()";
		StringBuilder sb = new StringBuilder();
		sb.append('(').append(values[0]);
		for (int i = 1; i < values.length; i++)
			sb.append(", ").append(values[i]);
		return sb.append(')').toString();
	}

	@Override
	public int elCount() {
		return values.length;
	}

	@Override
	public Value element(int i) {
		return values[i];
	}

	@Override
	public CstBytes data() {
		return CstBytes.EMPTY;
	}

	public static Value cast(Arguments args, ScopeData scope) {
		Value va = args.in(0), vb = args.in(1);
		Type ta = va.type;
		if (vb.type == ta) return vb;
		if (vb instanceof Bundle cb)
			return new Bundle(ta, cb.values);
		if (vb instanceof MutMap cb)
			return new Bundle(ta, cb.map.values().toArray(Value[]::new));
		return new Bundle(ta, new Value[] {vb});
	}

	public static Value con(Arguments args, ScopeData scope) {
		Bundle ca = (Bundle)args.in(0);
		Value vb = args.in(1);
		Value[] x = ca.values, elem;
		if (vb instanceof Bundle cb && vb.type.vtable == ca.type.vtable) {
			Value[] y = cb.values;
			if (y.length == 0) return ca;
			if (x.length == 0) return cb;
			elem = new Value[x.length + y.length];
			System.arraycopy(x, 0, elem, 0, x.length);
			System.arraycopy(y, 0, elem, x.length, y.length);
		} else {
			elem = Arrays.copyOf(x, x.length + 1);
			elem[x.length] = vb;
		}
		return new Bundle(elem);
	}

	public static Value rcon(Arguments args, ScopeData scope) {
		Value va = args.in(0);
		Bundle cb = (Bundle)args.in(1);
		Value[] y = cb.values, elem = new Value[y.length + 1];
		elem[0] = va;
		System.arraycopy(y, 0, elem, 1, y.length);
		return new Bundle(elem);
	}

	public static Value get(Arguments args, ScopeData scope) throws SignalError {
		Bundle ca = (Bundle)args.in(0);
		Value vb = args.in(1);
		Value[] xa = ca.values;
		Type ta = ca.type;
		if (args.ins() > 2) {
			if (vb instanceof CstInt cb && args.in(2) instanceof CstInt cc) {
				int l = xa.length;
				int idx0 = (int)cb.value, idx1 = (int)cc.value;
				if (idx0 < 0 && (idx0 += l) < 0) idx0 = 0;
				else if (idx0 > l) idx0 = l;
				if (idx1 < 0 && (idx1 += l) < 0) idx1 = 0;
				else if (idx1 > l) idx1 = l;
				if (idx0 == 0 && idx1 == l)
					return ca;
				if (idx0 >= idx1)
					return VOID;
				return new Bundle(ta, Arrays.copyOfRange(xa, idx0, idx1));
			}
		} else if (vb instanceof CstInt cb) {
			int idx = (int)cb.value;
			if (idx < 0 && (idx += xa.length) < 0 || idx >= xa.length)
				return VOID;
			return xa[idx];
		} else if (vb instanceof CstBytes cb) {
			int idx = ta.index(cb.string());
			return idx < 0 || idx >= xa.length ? VOID : xa[idx];
		} else if (vb instanceof Bundle cb) {
			Value[] elem = new Value[cb.values.length];
			for (int i = 0; i < elem.length; i++) {
				vb = cb.values[i];
				int idx;
				if (vb instanceof CstInt cb1) {
					idx = (int)cb1.value;
					if (idx < 0) idx += xa.length;
				} else if (vb instanceof CstBytes cb1) {
					idx = ta.index(cb1.string());
				} else return args.error("invalid index type in element " + i);
				elem[i] = idx < 0 || idx >= xa.length ? VOID : xa[idx];
			}
			return new Bundle(ta, elem);
		}
		return args.error("invalid index type");
	}

	public static Value len(Arguments args, ScopeData scope) {
		Value va = args.in(0);
		Bundle ca = (Bundle)va;
		return new CstInt(ca.values.length);
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		return new Bundle(type, elements);
	}

}