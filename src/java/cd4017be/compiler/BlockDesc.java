package cd4017be.compiler;

import cd4017be.compiler.Node.Vertex;
import cd4017be.compiler.instr.GetElement;
import cd4017be.util.IndexedSet;

/**
 * 
 * @author CD4017BE */
public class BlockDesc extends IndexedSet.Element {

	public final BlockDef def;
	public final Node[] outs;
	public final Vertex[] ins;
	public final int[] inLinks;
	public final BlockDesc[] inBlocks;
	public final String[] args;

	public BlockDesc(BlockDef def, int outs, int ins, int args) {
		this(def, outs, new int[ins], new String[args]);
	}

	public BlockDesc(BlockDef def, int outs, int[] ins, String[] args) {
		this.def = def;
		this.outs = new Node[outs];
		this.ins = new Vertex[ins.length];
		this.inLinks = ins;
		this.inBlocks = new BlockDesc[ins.length];
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

	public int ins() {
		return ins.length;
	}

	public int outs() {
		return outs.length;
	}

	public void setIns(Node node) {
		for (int i = 0; i < ins.length; i++)
			ins[i] = node.in[i];
	}

	public void setOuts(Node result) {
		for (int i = 0; i < outs.length; i++)
			outs[i] = result;
	}

	public void makeOuts(Node result, int idx) {
		if (outs.length == 1) outs[0] = result;
		else for (int i = 0; i < outs.length; i++)
			outs[i] = new GetElement(i).node(result, idx);
	}

	public void makeNode(Instruction instr, int idx) {
		Node node = new Node(instr, Node.INSTR, ins.length, idx);
		setIns(node);
		makeOuts(node, idx);
	}

	public void connectIn(int i, BlockDesc src, int pin) {
		inBlocks[i] = src;
		inLinks[i] = pin;
	}

	public void connect() {
		for (int i = 0; i < inBlocks.length; i++) {
			BlockDesc in = inBlocks[i];
			ins[i].connect(in == null ? null : in.outs[inLinks[i]]);
		}
	}

}
