package cd4017be.dfc.lang.instructions;

import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;

/**
 * @author cd4017be */
public class UnpackIns extends Instruction {

	private final int idx;
	private int in, out;

	public UnpackIns(int idx) {
		this.idx = idx;
	}

	@Override
	public Instruction setIO(int[] io) throws SignalError {
		checkIO(io, 3);
		this.out = io[0];
		this.in = io[2];
		return this;
	}

	@Override
	public void eval(Interpreter ip, Value[] vars) throws SignalError {
		Value v = vars[in];
		if (idx >= v.elements.length)
			throw new SignalError(~out, "value has too few elements");
		vars[out] = v.elements[idx];
	}

	public static Node node(int i, Node in, int idx) {
		Node n = new Node(new UnpackIns(i), Node.INSTR, 1, idx);
		n.in[0].connect(in);
		return n;
	}

}
