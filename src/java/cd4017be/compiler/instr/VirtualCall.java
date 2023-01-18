package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;


/**
 * 
 * @author CD4017BE */
public class VirtualCall implements Instruction, NodeAssembler {

	private final String name;

	public VirtualCall(String name) {
		this.name = name;
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context, int idx) {
		Node node = new Node(this, Node.INSTR, block.ins.length, idx);
		block.setIns(node);
		block.makeOuts(node, idx);
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) throws SignalError {
		Value v = args.in(0);
		Instruction vm = v.type.vtable.get(name);
		if (vm != null && (v = vm.eval(args, scope)) != null) return v;
		if (args.ins() == 2) vm = args.in(1).type.vtable.get('r' + name);
		return vm != null && (v = vm.eval(args, scope)) != null ? v
			: args.error("inputs don't support " + name);
	}

}
