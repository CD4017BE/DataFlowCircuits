package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;
import cd4017be.compiler.builtin.SwitchSelector;


/**
 * 
 * @author CD4017BE */
public class CstSwitch implements Instruction, SwitchAssembler {

	private final Value[] cases;

	public CstSwitch(Value... cases) {
		this.cases = cases;
	}

	@Override
	public Node switchNode(BlockDesc block, NodeContext context) {
		return new Node(this, Node.INSTR, 1);
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		Value val = args.in(0);
		for (int i = 0; i < cases.length; i++)
			if (cases[i].equals(val))
				return new SwitchSelector(i + 1, val);
		return new SwitchSelector(0, val);
	}

	public Node node(Node... in) {
		Node node = new Node(this, Node.INSTR, 1);
		node.in[0].connect(in[0]);
		Node sel = new Node(SwitchAssembler.SELECT, Node.SWT, cases.length + 2);
		sel.in[0].connect(node);
		for (int i = 1; i < in.length; i++)
			sel.in[i].connect(in[i]);
		return sel;
	}

}
