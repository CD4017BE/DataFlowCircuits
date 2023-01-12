package cd4017be.compiler.instr;

import java.util.ArrayList;
import java.util.HashMap;

import cd4017be.compiler.*;
import cd4017be.compiler.NodeAssembler.TextAutoComplete;
import cd4017be.compiler.builtin.ScopeData;


/**
 * 
 * @author CD4017BE */
public class IO implements Instruction, TextAutoComplete {

	public static final IO INSTANCE = new IO();

	private IO() {}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		return args.get(0);
	}

	@Override
	public void assemble(BlockDesc block, HashMap<String, Node> namedLinks) {
		if (block.outs.length + block.ins.length != 1 || block.args.length < 1)
			throw new IllegalArgumentException("wrong IO count");
		Node node = block.getIO(0, namedLinks);
		block.setOuts(node);
		block.setIns(node);
	}

	@Override
	public void
	getAutoCompletions(EditorGraph state, BlockDesc desc, int arg, ArrayList<String> list) {
		list.addAll(state.links.keySet());
	}

}
