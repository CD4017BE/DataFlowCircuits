package cd4017be.dfc.lang.instructions;

import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;
import modules.loader.Intrinsics;

/**
 * @author cd4017be */
public class PackIns extends Instruction {

	private int[] io;

	@Override
	public Instruction setIO(int[] io) throws SignalError {
		this.io = io;
		return this;
	}

	@Override
	public void eval(Interpreter ip, Value[] vars) throws SignalError {
		int l = io.length - 2;
		Value[] elem = l <= 0 ? Value.NO_ELEM : new Value[l];
		for (int i = 0; i < l; i++)
			elem[i] = vars[io[i + 2]];
		vars[io[0]] = Value.of(elem, Intrinsics.VOID);
	}

	public static Node node(int idx, Node... ins) {
		Node node = new Node(new PackIns(), Node.INSTR, ins.length, idx);
		for (int i = 0; i < ins.length; i++)
			node.in[i].connect(ins[i]);
		return node;
	}

}
