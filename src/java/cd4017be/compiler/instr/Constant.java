package cd4017be.compiler.instr;

import cd4017be.compiler.*;


/**
 * 
 * @author CD4017BE */
public class Constant implements NodeAssembler {

	private final Value value;

	public Constant(Value value) {
		this.value = value;
	}

	public Node node(int idx) {
		return new Node(value, Node.INSTR, 0, idx);
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
		if (block.ins.length != 0 || block.args.length != block.outs.length)
			throw new SignalError(idx, "wrong IO count");
		for (int i = 0; i < block.args.length; i++)
			block.outs[i] = new Node(Value.parse(block.args[0], context, idx, "value"), Node.INSTR, 0, idx);
	}

}
