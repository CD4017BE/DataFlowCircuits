package cd4017be.compiler;

import java.util.ArrayList;

/**
 * 
 * @author CD4017BE */
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

	public static int[] err(Macro macro, BlockDef def, int outs, int ins, String msg) {
		return macro.addNode(NodeOperator.ERROR, msg, ins).makeLinks(outs);
	}

	NodeAssembler IO = new TextAutoComplete() {
		@Override
		public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
			if (outs + ins != 1 || args.length < 1) return err(macro, def, outs, ins, "wrong IO count");
			return new int[] {macro.addIONode(args[0]).idx};
		}
		@Override
		public void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list) {
			list.addAll(macro.links.keySet());
		}
	};
	NodeAssembler VIRTUAL = (macro, def, outs, ins, args) -> {
		return macro.addNode(NodeOperator.VIRTUAL, def.id, ins).makeLinks(outs);
	};
	NodeAssembler ET = (macro, def, outs, ins, args) -> {
		if (ins != 1 || args.length < 1) return err(macro, def, outs, ins, "wrong IO count");
		Object data;
		try {
			data = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			data = args[0];
		}
		return macro.addNode(NodeOperator.EL_TYPE, data, ins).makeLinks(outs);
	};
	NodeAssembler NT = new TextAutoComplete() {
		@Override
		public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
			if (ins != args.length - 1) return err(macro, def, outs, ins, "wrong IO count");
			return macro.addNode(NodeOperator.NEW_TYPE, args, ins).makeLinks(outs);
		}
		@Override
		public void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list) {
			list.addAll(macro.def.module.types.keySet());
		}
	};
	NodeAssembler OP = (macro, def, outs, ins, args) -> {
		if (args.length < 1) return err(macro, def, outs, ins, "wrong IO count");
		return macro.addNode(NodeOperator.OPERATION, args[0], ins).makeLinks(outs);
	};
	NodeAssembler MV = new TextAutoComplete() {
		@Override
		public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
			if (ins != 3 || args.length < 1) return err(macro, def, outs, ins, "wrong IO count");
			VTable vt = macro.def.module.types.get(args[0]);
			return macro.addNode(NodeOperator.MATCH_VT, vt, ins).makeLinks(outs);
		}
		@Override
		public void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list) {
			list.addAll(macro.def.module.types.keySet());
		}
	};
	NodeAssembler CV = (macro, def, outs, ins, args) -> {
		if (ins != 4) return err(macro, def, outs, ins, "wrong IO count");
		return macro.addNode(NodeOperator.COMP_VT, null, ins).makeLinks(outs);
	};
	NodeAssembler CT = (macro, def, outs, ins, args) -> {
		if (ins != 4) return err(macro, def, outs, ins, "wrong IO count");
		return macro.addNode(NodeOperator.COMP_TYPE, null, ins).makeLinks(outs);
	};
	NodeAssembler ERR = (macro, def, outs, ins, args) -> {
		return macro.addNode(NodeOperator.ERROR, args.length > 0 ? args[0] : "error", ins).makeLinks(outs);
	};

}
