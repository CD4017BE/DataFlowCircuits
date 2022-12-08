package cd4017be.compiler;

import cd4017be.compiler.builtin.*;

/**
 * 
 * @author CD4017BE */
public interface Plugin {

	NodeAssembler assembler(String type, BlockDef def);

	Class<? extends Value> valueClass(String type);

	Plugin DEFAULT = new Plugin() {
		@Override
		public Class<? extends Value> valueClass(String type) {
			switch(type) {
			case "int": return CstInt.class;
			case "float": return CstFloat.class;
			case "bytes": return CstBytes.class;
			case "bundle": return Bundle.class;
			case "dyn": return DynOp.class;
			default: return Value.class;
			}
		}
		@Override
		public NodeAssembler assembler(String type, BlockDef def) {
			switch(type) {
			case "block": return new Macro(def);
			case "const": return new ConstList(def);
			case "io": return NodeAssembler.IO;
			case "to": return NodeAssembler.VIRTUAL;
			case "et": return NodeAssembler.ET;
			case "nt": return NodeAssembler.NT;
			case "op": return NodeAssembler.OP;
			case "mv": return NodeAssembler.MV;
			case "cv": return NodeAssembler.CV;
			case "ct": return NodeAssembler.CT;
			case "err": return NodeAssembler.ERR;
			default: return null;
			}
		}
	};

}
