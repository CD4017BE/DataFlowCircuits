package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;


/**
 * 
 * @author CD4017BE */
public class Constant implements Instruction {

	private final Value value;

	public Constant(Value value) {
		this.value = value;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		return value;
	}

	public Node node() {
		return new Node(this, Node.INSTR, 0);
	}

}
