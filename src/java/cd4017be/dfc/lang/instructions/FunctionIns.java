package cd4017be.dfc.lang.instructions;

import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;
import cd4017be.dfc.lang.builders.Function;

/**Implements an interpreted function call.
 * @author cd4017be */
public class FunctionIns extends Instruction {

	private final Function func;
	private int[] io;

	public FunctionIns(Function func) {
		this.func = func;
	}

	@Override
	public Instruction setIO(int[] io) throws SignalError {
		checkIO(io, func.par + 2);
		this.io = io;
		return this;
	}

	@Override
	public void eval(Interpreter ip, Value[] vars) throws SignalError {
		try {
			if (func.vars == null) func.load();
			Value[] vars1 = new Value[func.vars.length];
			for (int i = 1; i < io.length; i++)
				vars1[i - 1] = vars[io[i]];
			ip.eval(func.code, vars1, 0);
			vars[io[0]] = vars1[func.ret];
		} catch (SignalError e) {
			e.pos = ~io[0];
			throw e;
		}
	}

}
