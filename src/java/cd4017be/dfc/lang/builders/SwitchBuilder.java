package cd4017be.dfc.lang.builders;

import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.BlockDesc;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.NodeAssembler;
import cd4017be.dfc.lang.NodeContext;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.instructions.FunctionIns;

/**
 * @author cd4017be */
public class SwitchBuilder implements NodeAssembler {

	private final Function selFunc;

	public SwitchBuilder(BlockDef def) {
		this.selFunc = new Function(def);
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context, int idx)
	throws SignalError {
		Node sn = new Node(new FunctionIns(selFunc), Node.INSTR, selFunc.par, idx);
		Node[] args = block.getArgNodes(context, idx);
		if (block.ins() + args.length <= sn.in.length)
			throw new SignalError(idx, "wrong input pin count");
		for (int i = 0; i < args.length; i++)
			sn.in[i].connect(args[i]);
		int n = 0;
		for (int i = args.length; i < sn.in.length; i++, n++)
			block.ins[n] = sn.in[i];
		Node swt = new Node(null, Node.SWT, block.ins() - n + 1, idx);
		swt.in[0].connect(sn);
		for (int i = 1; i < swt.in.length; i++, n++)
			block.ins[n] = swt.in[i];
		block.makeOuts(swt, idx);
	}

}
