package cd4017be.compiler;

/**
 * 
 * @author CD4017BE */
@FunctionalInterface
public interface NodeAssembler {

	/**Assemble the given block.
	 * All node connections within the block should be fully established.
	 * The returned output nodes should not be used as input by any internal node.
	 * There should be no internal nodes with unused output.
	 * @param macro the macro being assembled into
	 * @param info the block to assemble
	 * @return [0..def.outCount-1] = output_node_index,
	 * [def.outCount..def.ios()-1] = input_node_index | input_node_pin << 24 */
	int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args);

	NodeAssembler IO = (macro, def, outs, ins, args) ->
		new int[] {macro.addIONode(args[0]).idx};
	NodeAssembler VIRTUAL = (macro, def, outs, ins, args) ->
		macro.addNode(NodeOperator.VIRTUAL, def.id, ins).makeLinks();

}
