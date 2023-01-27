package cd4017be.compiler.builtin;

import java.util.*;

import cd4017be.compiler.*;

/**
 * 
 * @author CD4017BE */
public class ScopeData extends Value {

	public static final Type SCOPE = Type.builtin("scope");
	public static final ScopeData ROOT = new ScopeData(Bundle.VOID);

	public final ScopeData parent;
	public final Value value;
	public final int path;
	public final ArrayList<Value> dynOps = new ArrayList<>();
	public final HashMap<Value, Value> globals;

	public ScopeData(Value value) {
		this(null, 0, value);
	}

	public ScopeData(ScopeData parent, int path, Value value) {
		super(SCOPE);
		if (value == null) throw new NullPointerException();
		this.parent = parent;
		this.path = path;
		this.value = value;
		this.globals = parent != null ? parent.globals : new HashMap<>();
	}

	@Override
	public int elCount() {
		return 2 + dynOps.size();
	}

	@Override
	public Value element(int i) {
		return switch(i) {
		case 0 -> parent == null ? Bundle.VOID : parent;
		case 1 -> value;
		default -> dynOps.get(i - 2);
		};
	}

	@Override
	public CstBytes data() {
		return new CstBytes(new byte[] {(byte)path, (byte)(path >> 8)});
	}

	public void compile(BlockDef def) throws SignalError {
		VTable comp = def.module.findType("compiler");
		if (comp == null) return;
		Instruction ins = comp.get("compile");
		if (ins == null) return;
		Value val = new IOStream(Type.of(comp, 0), System.out);
		ins.eval(new Arguments(val, new Bundle(dynOps.toArray(Value[]::new))), this);
	}

	public static Value rcast(Arguments args, ScopeData scope) {
		Type t = args.in(0).type;
		ScopeData sd = (ScopeData)args.in(1);
		return t == SCOPE ? sd
			: t == CstInt.CST_INT ? new CstInt(sd.path)
			: t == Bundle.BUNDLE ? new Bundle(sd.dynOps.toArray(Value[]::new))
			: null;
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		ScopeData val = new ScopeData(
			elements[0] instanceof ScopeData sd ? sd : null,
			data[0] & 0xff | data[1] << 8, elements[1]
		);
		for (int i = 2; i < elements.length; i++) {
			Value v = elements[i];
			val.dynOps.add(v);
			if (v instanceof DynOp o && o.isGlobal)
				val.globals.put(o.values[0], o);
		}
		return val;
	}
}
