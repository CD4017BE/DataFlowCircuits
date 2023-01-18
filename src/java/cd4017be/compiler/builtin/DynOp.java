package cd4017be.compiler.builtin;

import static java.nio.charset.StandardCharsets.UTF_8;

import cd4017be.compiler.Type;
import cd4017be.compiler.Value;


/**
 * @author CD4017BE */
public class DynOp extends Value {

	public final String op;
	public final Value[] args;

	public DynOp(Type type, String op, Value[] args) {
		super(type, true);
		this.op = op;
		this.args = args;
	}

	@Override
	public int elCount() {
		return args.length;
	}

	@Override
	public Value element(int i) {
		return args[i];
	}

	@Override
	public CstBytes data() {
		return new CstBytes(op);
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		return new DynOp(type, new String(data, UTF_8), elements);
	}
}
