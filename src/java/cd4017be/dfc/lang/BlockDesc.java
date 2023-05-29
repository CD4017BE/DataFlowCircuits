package cd4017be.dfc.lang;

import cd4017be.dfc.lang.Node.Vertex;
import cd4017be.dfc.lang.instructions.PackIns;
import cd4017be.dfc.lang.instructions.UnpackIns;
import cd4017be.util.IndexedSet;
import modules.dfc.module.Intrinsics;

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
		&& def == other.def
		&& outs.length == other.outs.length
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

	public void makeOuts(Node result, int idx) {
		if (outs.length == 1) outs[0] = result;
		else for (int i = 0; i < outs.length; i++)
			(outs[i] = new Node(new UnpackIns(i), Node.INSTR, 1, idx)).in[0].connect(result);
	}

	public void makeNode(Instruction instr, int idx) {
		Node node = new Node(instr, Node.INSTR, ins.length, idx);
		setIns(node);
		makeOuts(node, idx);
	}

	public void setIn(int i, Vertex v, int idx) throws SignalError {
		if ((def.vaSize & BlockDef.VAR_IN) != 0 && i == def.ins.length - 1) {
			int l = ins.length - def.ins.length + 1;
			if (l < 0) throw new SignalError(idx, "wrong input pin count");
			Node merge = new Node(new PackIns(), Node.INSTR, l, idx);
			v.connect(merge);
			for (int j = 0, k = ins.length - l; j < l; j++, k++)
				ins[k] = merge.in[j];
		} else if (i < ins.length) ins[i] = v;
		else throw new SignalError(idx, "wrong input pin count");
	}

	public void setOut(int i, Node node, int idx) throws SignalError {
		if ((def.vaSize & BlockDef.VAR_OUT) != 0 && i == def.outs.length - 1) {
			int l = outs.length - def.outs.length + 1;
			if (l < 0) throw new SignalError(idx, "wrong output pin count");
			for (int j = 0, k = ins.length - l; j < l; j++, k++)
				outs[k] = UnpackIns.node(j, node, idx);
		} else if (i < outs.length) outs[i] = node;
		else throw new SignalError(idx, "wrong output pin count");
	}

	public Node[] getArgNodes(NodeContext context, int idx) throws SignalError {
		int n = def.args.length;
		Node[] nodes = new Node[n];
		if (n == 0) return nodes;
		String[] args = context.args(this);
		if ((def.vaSize & BlockDef.VAR_ARG) != 0) {
			int l = args.length - --n;
			if (l < 0) throw new SignalError(idx, "wrong parameter count");
			ArgumentParser p = parser(n);
			Node merge = new Node(new PackIns(), Node.INSTR, l, idx);
			for (int i = 0; i < l; i++)
				merge.in[i].connect(p.parse(args[n + i], this, n + i, context, idx));
			nodes[n] = merge;
		} else if (args.length != n) throw new SignalError(idx, "wrong parameter count");
		for (int i = 0; i < n; i++)
			nodes[i] = parser(i).parse(args[i], this, i, context, idx);
		return nodes;
	}

	public ArgumentParser parser(int argidx) {
		ArgumentParser[] parsers = def.parsers;
		int l = parsers.length;
		return l == 0 ? Intrinsics.ERROR_ARG : parsers[argidx < l ? argidx : l - 1];
	}

	public void connectIn(int i, BlockDesc src, int pin) {
		inBlocks[i] = src;
		inLinks[i] = pin;
	}

	public Node inNode(int i) {
		BlockDesc in = inBlocks[i];
		return in == null ? null : in.outs[inLinks[i]];
	}

	public void connect() {
		for (int i = 0; i < inBlocks.length; i++)
			ins[i].connect(inNode(i));
	}

	public Value signal(int pin) {
		return null;
	}

}
