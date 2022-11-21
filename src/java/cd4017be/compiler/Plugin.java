package cd4017be.compiler;


/**
 * 
 * @author CD4017BE */
public interface Plugin {

	NodeAssembler assembler(String type, BlockDef def);

	Plugin DEFAULT = (type, def) -> {
		switch(type) {
		case "box": return new Macro(def);
		case "io": return NodeAssembler.IO;
		case "typeop": return NodeAssembler.VIRTUAL;
		default: return null;
		}
	};

}
