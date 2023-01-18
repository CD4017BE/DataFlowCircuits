package cd4017be.compiler;

import java.util.ArrayList;

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

	/**Collect text argument auto-completion options
	 * @param block to get auto-completions for
	 * @param arg index of argument to complete
	 * @param list to collect auto-complete options
	 * @param context in which block it is assembled */
	default void getAutoCompletions(
		BlockDesc block, int arg, ArrayList<String> list, NodeContext context
	) {}

}
