package cd4017be.dfc.lang;

import java.util.ArrayList;

/**
 * @author cd4017be */
public interface ArgumentParser {

	/**Collect text argument auto-completion options
	 * @param block to get auto-completions for
	 * @param arg index of argument to complete
	 * @param list to collect auto-complete options
	 * @param context in which the block is assembled */
	default void getAutoCompletions(BlockDesc block, int arg, ArrayList<String> list, NodeContext context) {}

	/**Parse the given text argument into a Node
	 * @param arg text argument to parse
	 * @param block being assembled
	 * @param argidx the argument index on block being parsed
	 * @param context in which the block is assembled
	 * @param idx block index
	 * @return argument parsed as Node
	 * @throws SignalError if parsing failed */
	Node parse(String arg, BlockDesc block, int argidx, NodeContext context, int idx) throws SignalError;

}
