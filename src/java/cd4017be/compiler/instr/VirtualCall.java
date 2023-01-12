package cd4017be.compiler.instr;

import java.util.HashMap;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;
import cd4017be.compiler.builtin.SignalError;


/**
 * 
 * @author CD4017BE */
public class VirtualCall implements Instruction, NodeAssembler {

	private final String name;

	public VirtualCall(String name) {
		this.name = name;
	}

	@Override
	public void assemble(BlockDesc block, HashMap<String, Node> namedLinks) {
		Node node = new Node(this, Node.INSTR, block.ins.length);
		block.setIns(node);
		block.makeOuts(node);
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		Value v = args.in(0);
		Instruction vm = v.type.vtable.get(name);
		if (vm != null && (v = vm.eval(args, scope)) != null) return v;
		if (args.ins() == 2) vm = args.in(1).type.vtable.get('r' + name);
		return vm != null && (v = vm.eval(args, scope)) != null ? v
			: new SignalError("inputs don't support " + name);
	}
	/*
	static SignalError revOp(NodeState a, NodeState ns, Value vb, String op) {
		VirtualMethod vm = vb.type.vtable.get(op);
		return vm != null ? vm.run(a, ns) : new SignalError("y doesn't support " + op);
	}
	*/
}
