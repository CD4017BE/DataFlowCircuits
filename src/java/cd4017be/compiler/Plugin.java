package cd4017be.compiler;


/**
 * 
 * @author CD4017BE */
public interface Plugin {

	NodeAssembler assembler(String type, BlockDef def);

	Plugin DEFAULT = (type, def) -> {
		switch(type) {
		case "block": return new Macro(def);
		case "const": return NodeAssembler.CONST;
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
	};

}
