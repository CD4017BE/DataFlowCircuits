package cd4017be.compiler.builtin;

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import cd4017be.compiler.Arguments;
import cd4017be.compiler.SignalError;
import cd4017be.compiler.Type;
import cd4017be.compiler.Value;


public class MutMap extends Value {

	public static final Type MAP = Type.builtin("map");

	public final LinkedHashMap<Value, Value> map = new LinkedHashMap<>();
	private Value[] arr;

	public MutMap() {
		super(MAP, true);
	}

	@Override
	public int elCount() {
		return map.size() * 2;
	}

	@Override
	public Value element(int i) {
		if (arr == null) {
			arr = new Value[elCount()];
			int j = 0;
			for (Entry<Value, Value> e : map.entrySet()) {
				arr[j++] = e.getKey();
				arr[j++] = e.getValue();
			}
		}
		return arr[i];
	}

	@Override
	public CstBytes data() {
		return CstBytes.EMPTY;
	}

	public static Value con(Arguments args, ScopeData scope) {
		Value a = args.in(0), vb = args.in(1);
		MutMap ma = a instanceof MutMap m ? m : new MutMap();
		if (vb instanceof MutMap mb) {
			ma.arr = null;
			ma.map.putAll(mb.map);
			return ma;
		}
		return null;
	}

	public static Value get(Arguments args, ScopeData scope) throws SignalError {
		Value a = args.in(0);
		if (!(a instanceof MutMap m)) return Bundle.VOID;
		Value b = m.map.get(args.in(1));
		return b == null ? Bundle.VOID : b;
	}

	public static Value set(Arguments args, ScopeData scope) throws SignalError {
		Value a = args.in(0), b = args.in(1), c = args.in(2);
		MutMap m = a instanceof MutMap mm ? mm : new MutMap();
		m.arr = null;
		m.map.put(b, c);
		return m;
	}

	public static Value len(Arguments args, ScopeData scope) {
		Value va = args.in(0);
		return new CstInt(va instanceof MutMap m ? m.map.size() : 0);
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		MutMap map = new MutMap();
		for (int i = 0; i < elements.length; i += 2)
			map.map.put(elements[i], elements[i+1]);
		return map;
	}

}
