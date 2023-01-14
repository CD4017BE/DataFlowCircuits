package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;


/**
 * 
 * @author CD4017BE */
public class Abort implements Instruction {

	private final String msg;

	public Abort(String msg) {
		this.msg = msg;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) throws SignalError {
		String msg = this.msg;
		if (args.ins() != 0) {
			Object[] values = new Object[args.ins()];
			for (int i = 0; i < values.length; i++)
				values[i] = args.in(i);
			msg = msg.formatted(values);
		}
		return args.error(msg);
	}

}
