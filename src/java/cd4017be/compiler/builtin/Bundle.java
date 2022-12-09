package cd4017be.compiler.builtin;

import static cd4017be.compiler.LoadingCache.CORE;
import java.util.Arrays;
import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class Bundle extends Value {

	public static final Type BUNDLE = new Type(CORE.findType("bundle"), 0).unique();
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

	public static SignalError cast(NodeState a, NodeState ns) {
		Type ta = a.value.type;
		Value vb = ns.in(1).value;
		if (vb.type == ta) return ns.out(vb, null);
		if (vb instanceof Bundle cb)
			return ns.out(new Bundle(ta, cb.values), null);
		return ns.out(new Bundle(ta, new Value[] {vb}), null);
	}

	public static SignalError con(NodeState a, NodeState ns) {
		Bundle ca = (Bundle)a.value;
		Value[] x = ca.values, elem;
		Value vb = ns.in(1).value;
		if (vb instanceof Bundle cb && vb.type.vtable == ca.type.vtable) {
			Value[] y = cb.values;
			if (y.length == 0) return ns.out(ca, null);
			if (x.length == 0) return ns.out(cb, null);
			elem = new Value[x.length + y.length];
			System.arraycopy(x, 0, elem, 0, x.length);
			System.arraycopy(y, 0, elem, x.length, y.length);
		} else {
			elem = Arrays.copyOf(x, x.length + 1);
			elem[x.length] = vb;
		}
		return ns.out(new Bundle(elem), null);
	}

	public static SignalError rcon(NodeState a, NodeState ns) {
		Bundle cb = (Bundle)ns.in(1).value;
		Value[] y = cb.values, elem = new Value[y.length + 1];
		elem[0] = a.value;
		System.arraycopy(y, 0, elem, 1, y.length);
		return ns.out(new Bundle(elem), null);
	}

	public static SignalError get(NodeState a, NodeState ns) {
		Bundle ca = (Bundle)a.value;
		Value[] xa = ca.values;
		Type ta = ca.type;
		Value vb = ns.in(1).value;
		if (ns.ins() > 2) {
			if (vb instanceof CstInt cb && ns.in(2).value instanceof CstInt cc) {
				int l = xa.length;
				int idx0 = (int)cb.value, idx1 = (int)cc.value;
				if (idx0 < 0 && (idx0 += l) < 0) idx0 = 0;
				else if (idx0 > l) idx0 = l;
				if (idx1 < 0 && (idx1 += l) < 0) idx1 = 0;
				else if (idx1 > l) idx1 = l;
				if (idx0 == 0 && idx1 == l)
					return ns.out(ca, null);
				if (idx0 >= idx1)
					return ns.out(VOID, null);
				return ns.out(new Bundle(ta, Arrays.copyOfRange(xa, idx0, idx1)), null);
			}
		} else if (vb instanceof CstInt cb) {
			int idx = (int)cb.value;
			if (idx < 0 && (idx += xa.length) < 0 || idx >= xa.length)
				return ns.out(VOID, null);
			return ns.out(xa[idx], null);
		} else if (vb instanceof CstBytes cb) {
			int idx = ta.index(cb.toString());
			return ns.out(idx < 0 || idx >= xa.length ? VOID : xa[idx], null);
		} else if (vb instanceof Bundle cb) {
			Value[] elem = new Value[cb.values.length];
			for (int i = 0; i < elem.length; i++) {
				vb = cb.values[i];
				int idx;
				if (vb instanceof CstInt cb1) {
					idx = (int)cb1.value;
					if (idx < 0) idx += xa.length;
				} else if (vb instanceof CstBytes cb1) {
					idx = ta.index(cb1.toString());
				} else return new SignalError("invalid index type in element " + i);
				elem[i] = idx < 0 || idx >= xa.length ? VOID : xa[idx];
			}
			return ns.out(new Bundle(ta, elem), null);
		}
		return new SignalError("invalid index type");
	}

	public static SignalError len(NodeState a, NodeState ns) {
		Bundle ca = (Bundle)a.value;
		return ns.out(new CstInt(ca.values.length), null);
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		return new Bundle(type, elements);
	}

}