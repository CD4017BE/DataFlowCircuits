package cd4017be.dfc.lang.builders;

import java.lang.invoke.MethodHandle;

import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.instructions.ConstantIns;
import modules.dfc.core.Intrinsics;

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
		Node sn = new Node(selFunc.makeVirtual(block.def), Node.INSTR, selFunc.par - 1, idx);
		Node[] args = block.getArgNodes(context, idx);
		if (block.ins() + args.length <= sn.in.length)
			throw new SignalError(idx, "wrong input pin count");
		for (int i = 0; i < args.length; i++)
			sn.in[i].connect(args[i]);
		int n = 0;
		for (int i = args.length; i < sn.in.length - 1; i++, n++)
			block.ins[n] = sn.in[i];
		int m = block.ins() - n;
		Node swt = new Node(null, Node.SWT, m + 1, idx);
		sn.in[sn.in.length - 1].connect(ConstantIns.node(
			new Value(Intrinsics.INT, Value.NO_ELEM, Value.NO_DATA, m), idx
		));
		swt.in[0].connect(sn);
		for (int i = 1; i < swt.in.length; i++, n++)
			block.ins[n] = swt.in[i];
		block.makeOuts(swt, idx);
	}

	@Override
	public void setIntrinsic(MethodHandle impl) {
		selFunc.setIntrinsic(impl);
	}

	@Override
	public boolean hasCircuit() {
		return selFunc.hasCircuit();
	}

}
