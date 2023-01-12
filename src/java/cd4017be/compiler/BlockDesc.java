package cd4017be.compiler;

import cd4017be.compiler.Node.Vertex;
import cd4017be.compiler.instr.GetElement;

/**
 * 
 * @author CD4017BE */
public class BlockDesc {

	public final BlockDef def;
	public final Node[] outs;
	public final Vertex[] ins;
	public final int[] inLinks;
	public final String[] args;

	public BlockDesc(BlockDef def, int outs, int ins, int args) {
		this(def, outs, new int[ins], new String[args]);
	}

	public BlockDesc(BlockDef def, int outs, int[] ins, String[] args) {
		this.def = def;
		this.outs = new Node[outs];
		this.ins = new Vertex[ins.length];
		this.inLinks = ins;
		this.args = args;
	}

	@Override
	public int hashCode() {
		return ((def.hashCode() * 31 + outs.length) * 31 + ins.length) * 31 + args.length;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		return obj instanceof BlockDesc other
		&& def == other.def && outs == other.outs
		&& ins.length == other.ins.length
		&& args.length == other.args.length;
	}

	public void setIns(Node node) {
		for (int i = 0; i < ins.length; i++)
			ins[i] = node.in[i];
	}

	public void setOuts(Node result) {
		for (int i = 0; i < outs.length; i++)
			outs[i] = result;
	}

	public void makeOuts(Node result) {
		if (outs.length == 1) outs[0] = result;
		else for (int i = 0; i < outs.length; i++)
			outs[i] = new GetElement(i).node(result);
	}

	public void makeNode(Instruction instr) {
		Node node = new Node(instr, Node.INSTR, ins.length);
		setIns(node);
		makeOuts(node);
	}

}
