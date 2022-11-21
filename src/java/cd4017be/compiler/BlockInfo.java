package cd4017be.compiler;


/**
 * 
 * @author CD4017BE */
public class BlockInfo {

	public final BlockDef def;
	public final int outs;
	public final int[] ins;
	public final String[] args;

	public BlockInfo(BlockDef def, int outs, int ins, int args) {
		this(def, outs, new int[ins], new String[args]);
	}

	public BlockInfo(BlockDef def, int outs, int[] ins, String[] args) {
		this.def = def;
		this.outs = outs;
		this.ins = ins;
		this.args = args;
	}

	@Override
	public int hashCode() {
		return ((def.hashCode() * 31 + outs) * 31 + ins.length) * 31 + args.length;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		return obj instanceof BlockInfo other
		&& def == other.def && outs == other.outs
		&& ins.length == other.ins.length
		&& args.length == other.args.length;
	}

}
