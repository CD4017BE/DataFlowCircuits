package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;
import cd4017be.compiler.builtin.SwitchSelector;


/**
 * 
 * @author CD4017BE */
public class TypeSwitch implements Instruction, SwitchAssembler {

	private final VTable[] cases;

	public TypeSwitch(VTable... cases) {
		this.cases = cases;
	}

	@Override
	public Node switchNode(BlockDesc block, NodeContext context, int idx) {
		return new Node(this, Node.INSTR, 1, idx);
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		Value val = args.in(0);
		VTable type = val.type.vtable;
		for (int i = 0; i < cases.length; i++)
			if (cases[i] == type)
				return new SwitchSelector(i + 1, null);
		return new SwitchSelector(0, null);
	}

}
