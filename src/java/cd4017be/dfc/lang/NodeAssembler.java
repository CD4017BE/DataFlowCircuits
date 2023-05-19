package cd4017be.dfc.lang;

import java.lang.invoke.MethodHandle;

/**
 * 
 * @author CD4017BE */
public interface NodeAssembler {

	/**Assemble the given block.
	 * All node connections within the block and to named links in context should be fully established.
	 * All connections to external nodes are established by assigning them to {@code block.ins[]} and {@code block.outs[]}
	 * @param block to assemble
	 * @param context in which it is assembled */
	void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError;

	default Instruction makeVirtual(BlockDef def) {
		return null;
	}

	default void setIntrinsic(MethodHandle impl) {
		throw new UnsupportedOperationException();
	}

}
