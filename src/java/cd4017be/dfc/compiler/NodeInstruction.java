package cd4017be.dfc.compiler;

import cd4017be.dfc.graph.Node;
import cd4017be.dfc.graph.Node.Vertex;
import cd4017be.dfc.lang.Signal;

/**
 * Linked queue of nodes to translate into instructions.
 * @author CD4017BE */
public class NodeInstruction {

	/** the node to translate */
	public final Node node;
	/** the instruction insertion point */
	public final Instruction after;
	/** the next queue entry */
	NodeInstruction next;

	public NodeInstruction(NodeInstruction prev, Vertex v, Instruction after) {
		this(prev, v.src, after);
	}

	public NodeInstruction(NodeInstruction prev, Node node, Instruction after) {
		this.node = node;
		this.after = after;
		if (prev != null) {
			next = prev.next;
			prev.next = this;
		}
	}

	public Signal out(int i) {
		return node.out[i];
	}

	public Signal in(int i) {
		return node.input(i).signal();
	}

	public boolean evalConst(Signal s) {
		if (!s.isConst()) return false;
		s.type.evalConst(this, s.value);
		return true;
	}

	public NodeInstruction evalIns(int... ins) {
		NodeInstruction ni = this;
		for (int i = 0; i < ins.length; i++) {
			Node in = node.input(ins[i]).src;
			if (in != null)
				ni = new NodeInstruction(ni, in, i == 0 ? ni.after : ni.after.add(null));
		}
		return ni;
	}

	@SuppressWarnings("unchecked")
	public <T> T data() {
		return (T)node.data;
	}

}
