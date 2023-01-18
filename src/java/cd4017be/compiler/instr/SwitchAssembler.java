package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.SwitchSelector;

/**
 * @author cd4017be */
public interface SwitchAssembler extends NodeAssembler {

	Instruction SELECT = (args, scope) -> args.in(((SwitchSelector)args.in(0)).path + 1);

	Node switchNode(BlockDesc block, NodeContext context, int idx) throws SignalError;

	@Override
	default void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
		Node swt = switchNode(block, context, idx);
		int n = swt.in.length, m;
		for (int i = 0; i < n; i++)
			block.ins[i] = swt.in[i];
		Node sel = new Node(SELECT, Node.SWT, block.ins.length - n + 1, idx);
		m = sel.in.length;
		sel.in[0].connect(swt);
		for (int i = 1; i < m; i++)
			block.ins[n++] = sel.in[i];
		block.makeOuts(sel, idx);
	}


}
