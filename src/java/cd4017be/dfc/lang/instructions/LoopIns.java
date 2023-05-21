package cd4017be.dfc.lang.instructions;

import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;

/**Implements an interpreted loop construct
 * @author cd4017be */
public class LoopIns extends Instruction {

	private final Instruction[] body;
	private final int init, next, out, scope0, scope1;

	public LoopIns(Instruction[] body, int init, int next, int out, int scope0, int scope1) {
		this.body = body;
		this.init = init;
		this.next = next;
		this.out = out;
		this.scope0 = scope0;
		this.scope1 = scope1;
	}

	@Override
	public Instruction setIO(int[] io) throws SignalError {
		throw new SignalError(~io[0], "can't dynamically call loop");
	}

	@Override
	public void eval(Interpreter ip, Value[] vars) throws SignalError {
		Value c = vars[init];
		vars[out] = c;
		while (c.value != 0) {
			vars[scope1] = vars[scope0];
			ip.eval(body, vars, out);
			c = vars[next];
			vars[out] = c;
		}
	}

}
