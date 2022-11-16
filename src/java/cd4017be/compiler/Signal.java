package cd4017be.compiler;

/**
 * 
 * @author CD4017BE */
public class Signal {

	/** Signal used to represent disconnected inputs */
	public static final Signal DISCONNECTED = new Signal();

	public Signal[] src;
	public Object op;
	public Type type;
	public Value value;
	public boolean sideeffect;

	public Signal(Value value, Signal... src) {
		this.src = src;
		this.op = "$v";
		this.type = value.decl.type;
		this.value = value;
		this.sideeffect = true;
		for (int i = 0; i < src.length; i++)
			if (!src[i].sideeffect)
				src[i] = null;
	}

	public Signal(Signal... src) {
		this(Type.VOID, null, null, src);
	}

	public Signal(Type type, Object op, Signal... src) {
		this(type, null, op, src);
	}

	public Signal(Type type, Value value, Object op, Signal... src) {
		this.src = src;
		this.op = op;
		this.type = type;
		this.value = value;
		for (Signal s : src)
			sideeffect |= s.sideeffect;
	}

	public Signal cst() {
		(this.value = new Value(this)).id = -1;
		return this;
	}

	public Signal dyn() {
		(this.value = new Value(this)).id = 0;
		return this;
	}
}
