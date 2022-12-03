package cd4017be.compiler;

import java.util.ArrayList;

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

	public interface TextAutoComplete extends NodeAssembler {
		/**Collect text argument auto-completion options
		 * @param macro the macro being assembled into
		 * @param def the block type being assembled
		 * @param arg index of argument to complete
		 * @param list to collect auto-complete options */
		void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list);
	}

	NodeAssembler IO = new TextAutoComplete() {
		@Override
		public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
			return new int[] {macro.addIONode(args[0]).idx};
		}
		@Override
		public void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list) {
			list.addAll(macro.links.keySet());
		}
	};
	NodeAssembler VIRTUAL = (macro, def, outs, ins, args) ->
		macro.addNode(NodeOperator.VIRTUAL, def.id, ins).makeLinks();
	NodeAssembler ET = (macro, def, outs, ins, args) -> {
		Object data;
		try {
			data = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			data = args[0];
		}
		return macro.addNode(NodeOperator.EL_TYPE, data, ins).makeLinks();
	};
	NodeAssembler NT = new TextAutoComplete() {
		@Override
		public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
			String arg = args[0];
			int n = 0;
			int i = arg.lastIndexOf('#');
			if (i >= 0) {
				n = Integer.parseInt(arg.substring(i + 1));
				arg = arg.substring(0, i);
			}
			VTable vt = macro.def.module.types.get(arg);
			return macro.addNode(NodeOperator.NEW_TYPE, new Type(vt, n), ins).makeLinks();
		}
		@Override
		public void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list) {
			list.addAll(macro.def.module.types.keySet());
		}
	};
	NodeAssembler OP = (macro, def, outs, ins, args) ->
		macro.addNode(NodeOperator.OPERATION, args[0], ins).makeLinks();
	NodeAssembler MV = new TextAutoComplete() {
		@Override
		public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
			VTable vt = macro.def.module.types.get(args[0]);
			return macro.addNode(NodeOperator.MATCH_VT, vt, ins).makeLinks();
		}
		@Override
		public void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list) {
			list.addAll(macro.def.module.types.keySet());
		}
	};
	NodeAssembler CV = (macro, def, outs, ins, args) ->
		macro.addNode(NodeOperator.COMP_VT, null, ins).makeLinks();
	NodeAssembler CT = (macro, def, outs, ins, args) ->
		macro.addNode(NodeOperator.COMP_TYPE, null, ins).makeLinks();
	NodeAssembler ERR = (macro, def, outs, ins, args) ->
		macro.addNode(NodeOperator.ERROR, args[0], ins).makeLinks();
	NodeAssembler CONST = new TextAutoComplete() {
		@Override
		public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
			Value val = def.module.signals.get(args[0]);
			return macro.addNode(NodeOperator.CONST, val, ins).makeLinks();
		}
		@Override
		public void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list) {
			list.addAll(def.module.signals.keySet());
		}
	};

}
