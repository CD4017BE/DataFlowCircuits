package cd4017be.compiler.instr;

import java.util.HashMap;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;


/**
 * @author CD4017BE */
public class LoopEntrance implements NodeAssembler, Instruction {

	public static final LoopEntrance DO = new LoopEntrance();

	private LoopEntrance() {}

	@Override
	public void assemble(BlockDesc block, HashMap<String, Node> namedLinks) {
		Node node0 = LoopExit.CONTINUE.node(block.ins.length);
		block.setIns(node0);
		Node node1 = new Node(this, Node.BEGIN, 1);
		node1.in[0].connect(node0);
		block.makeOuts(node1);
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		return scope.value;
	}

}
