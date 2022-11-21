package cd4017be.compiler;

/**
 * 
 * @author CD4017BE */
public class BlockDef {

	static final int VAR_OUT = 1, VAR_IN = 2, VAR_ARG = 4;

	public final Module module;
	public final String id, type;
	public final String[] ins, outs, args;
	public final BlockModel model;
	public final NodeAssembler assembler;
	public final int vaSize;
	public String name;

	public BlockDef(Module module, String id, String type, String[] ins, String[] outs, String[] args, BlockModel model) {
		this.module = module;
		this.id = id;
		this.ins = ins;
		this.outs = outs;
		this.args = args;
		this.model = model;
		this.vaSize = 0;
		this.type = type;
		this.assembler = module == null ? null : module.assembler(type, this);
	}

	public int ins(int size) {
		int l = ins.length;
		return (vaSize & VAR_IN) == 0 ? l : l + size - 1;
	}

	public int outs(int size) {
		int l = outs.length;
		return (vaSize & VAR_OUT) == 0 ? l : l + size - 1;
	}

	public int args(int size) {
		int l = args.length;
		return (vaSize & VAR_ARG) == 0 ? l : l + size - 1;
	}

	public boolean varSize() {
		return vaSize != 0;
	}

}
