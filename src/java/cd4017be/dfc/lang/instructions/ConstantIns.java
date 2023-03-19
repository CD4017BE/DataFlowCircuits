package cd4017be.dfc.lang.instructions;

import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;

/**
 * @author cd4017be */
public class ConstantIns extends Instruction {

	private final Value val;
	private int out;

	public ConstantIns(Value val) {
		this.val = val;
	}

	@Override
	public Instruction setIO(int[] io) throws SignalError {
		checkIO(io, 2);
		this.out = io[0];
		return this;
	}

	@Override
	public void eval(Interpreter ip, Value[] vars) throws SignalError {
		vars[out] = val;
	}

	public static Node node(Value val, int idx) {
		return new Node(new ConstantIns(val), Node.INSTR, 0, idx);
	}

}
