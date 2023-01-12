package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;

/**
 * 
 * @author CD4017BE */
public class GetElement implements Instruction {

	private final int idx;

	public GetElement(int idx) {
		this.idx = idx;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		return args.in(0).element(idx);
	}

	public Node node(Node in) {
		Node node = new Node(this, Node.INSTR, 1);
		node.in[0].connect(in);
		return node;
	}

}
