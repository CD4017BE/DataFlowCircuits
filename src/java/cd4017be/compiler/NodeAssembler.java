package cd4017be.compiler;

import static cd4017be.compiler.ops.StandardOps.PASS;

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
	int[] assemble(Macro macro, String arg);

	final NodeAssembler IO = (macro, arg) -> new int[] {
		macro.links.computeIfAbsent(arg, name -> macro.addNode(PASS, null, 1)).idx
	};

}
