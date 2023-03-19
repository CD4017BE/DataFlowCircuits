package cd4017be.dfc.lang.instructions;

import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;

/**Implements an interpreted switch construct
 * @author cd4017be */
public class SwitchIns extends Instruction {

	private final Instruction[][] branches;
	private int[] io;

	/**
	 * @param branches
	 * @param io [0] = out, [1] = scope, [2] = cond, [3...] = branches */
	public SwitchIns(Instruction[][] branches, int[] io) {
		this.branches = branches;
		this.io = io;
	}

	@Override
	public Instruction setIO(int[] io) throws SignalError {
		throw new SignalError(io[0], "can't dynamically call switch");
	}

	@Override
	public void eval(Interpreter ip, Value[] vars) throws SignalError {
		Value s = vars[io[2]];
		int br = (int)s.value, l = branches.length, out = io[0];
		if (br >= l) throw new SignalError(out, "branch index out of range: " + br);
		if (br >= 0) {
			vars[out] = vars[io[1]];
			ip.eval(branches[br], vars, out);
			vars[out] = vars[io[3 + br]];
		} else {
			Value[] elem = s.elements;
			if (elem.length != l) throw new SignalError(out, "wrong number of branch scopes: " + elem.length);
			Instruction impl = s.type.lookup("switch");
			if (impl == null) throw new SignalError(out, s.type + " doesn't implement switch");
			for (int i = 0; i < l; i++) {
				vars[out] = elem[i];
				ip.eval(branches[i], vars, out);
			}
			impl.setIO(io).eval(ip, vars);
		}
	}

}
