package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.Bundle;
import cd4017be.compiler.builtin.ScopeData;

/**
 * 
 * @author CD4017BE */
public class SetElement implements Instruction {

	private final int idx;

	public SetElement(int idx) {
		this.idx = idx;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) throws SignalError {
		if (!(args.in(0) instanceof Bundle op && idx < op.values.length))
			return args.error((idx + 1) + " elements bundle expected");
		op.values[idx] = args.in(1);
		return op;
	}

}
