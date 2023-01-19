package cd4017be.compiler.instr;

import java.util.Objects;
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
	public Node switchNode(BlockDesc block, NodeContext context, int idx) {
		return new Node(this, Node.INSTR, 1, idx);
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		Value val = args.in(0);
		for (int i = 0; i < cases.length; i++)
			if (Objects.equals(cases[i], val))
				return new SwitchSelector(i + 1, null);
		return new SwitchSelector(0, null);
	}

}
