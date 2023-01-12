package cd4017be.compiler.instr;

import java.util.HashMap;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;
import cd4017be.compiler.builtin.SwitchSelector;


/**
 * 
 * @author CD4017BE */
public class LoopExit implements Instruction, NodeAssembler {

	public static final LoopExit LOOP = new LoopExit();
	public static final Goto BREAK = Goto.ELSE, CONTINUE = new Goto(1);

	private LoopExit() {}

	@Override
	public void assemble(BlockDesc block, HashMap<String, Node> namedLinks) {
		Node node = new Node(this, Node.END, 1);
		block.setIns(node);
		block.makeOuts(node);
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		return ((SwitchSelector)args.in(0)).value;
	}

}
