package cd4017be.compiler;

/**
 * 
 * @author CD4017BE */
public class Value {

	public static final Value VOID = new Value(Type.VOID, null);

	public final Type type;
	public Object op;
	public Value[] args;

	public Value(Type type, Object op, Value... args) {
		this.type = type;
		this.op = op;
		this.args = args;
	}

	public SideEffects effect(SideEffects... args) {
		return new SideEffects(SideEffects.combine(args), null, this);
	}

}
