package cd4017be.compiler.instr;

import java.util.HashMap;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;
import cd4017be.compiler.builtin.SwitchSelector;


/**
 * 
 * @author CD4017BE */
public class TypeSwitch implements Instruction, NodeAssembler {

	private final VTable[] cases;

	public TypeSwitch(VTable... cases) {
		this.cases = cases;
	}

	@Override
	public void assemble(BlockDesc block, HashMap<String, Node> namedLinks) {
		Node node = new Node(this, Node.INSTR, 1);
		Node sel = new Node(CstSwitch.SELECT, Node.SWT, cases.length + 2);
		block.setIns(sel);
		block.makeOuts(sel);
		sel.in[0].connect(node);
		block.ins[0] = node.in[0];
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		Value val = args.in(0);
		VTable type = val.type.vtable;
		for (int i = 0; i < cases.length; i++)
			if (cases[i] == type)
				return new SwitchSelector(i + 1, val);
		return new SwitchSelector(0, val);
	}

}
