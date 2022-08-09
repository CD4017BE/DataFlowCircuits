package cd4017be.dfc.graph;

import cd4017be.dfc.compiler.NodeCompiler;
import cd4017be.dfc.lang.*;

/**
 * 
 * @author CD4017BE */
public class Node {

	public static final Node NULL = new Node(null, -1, new BlockDef(null, 1, 0, false), 1);
	static {
		NULL.def.behavior = Behavior.NULL;
		NULL.def.compiler = NodeCompiler.NULL;
		NULL.out = Signal.NULL;
	}

	public final BlockDef def;
	private final Vertex[] io;
	public final Macro macro;
	public int idx;
	public Object data;
	public Signal out;
	public SignalError error;
	Node nextUpdate;
	int updating, compiled;

	public Node(Macro macro, int idx, BlockDef def, int ioCount) {
		this.macro = macro;
		this.idx = idx;
		this.def = def;
		this.io = new Vertex[ioCount];
		for (int i = 1; i < ioCount; i++)
			new Vertex(this, i);
	}

	/**Connect the given input to the given node.
	 * @param i input index
	 * @param src the source node to connect
	 * @param c the current context */
	public void connect(int i, Node src, Context c) {
		Vertex in = io[i + 1];
		if (src != null && in.src == src) return;
		if (in.src != null) {
			Vertex v = in.src.io[0];
			if (v == in) in.src.io[0] = in.next;
			else for (Vertex u; v != null; v = u)
				if ((u = v.next) == in) {
					v.next = in.next;
					break;
				}
		}
		in.src = src;
		if (src != null && src != NULL) {
			in.next = src.io[0];
			src.io[0] = in;
		} else in.next = null;
		if (src == null || src.out != null)
			c.updateNode(this, i + 1);
	}

	/**@param min minimum argument count
	 * @return the argument list of this node (length >= min)*/
	public String[] arguments(int min) {
		return macro.arguments(this, min);
	}

	/**@param i input index
	 * @return the node currently connected to input i (may be null) */
	public Node input(int i) {
		return io[i + 1].src;
	}

	/**@param i input index
	 * @param c the current context
	 * @return the node connected (possibly during this call) to input i. */
	public Node getInput(int i, Context c) {
		Vertex v = io[i + 1];
		if (v.src == null)
			macro.connectInput(this, i, c);
		return v.src;
	}

	public void updateOutput(Signal s, Context c) {
		if (out == (out = s) || s == null) return;
		for (Vertex v = io[0]; v != null; v = v.next)
			c.updateNode(v.dst, v.pin);
	}

	public void updateChngOutput(Signal s, Context c) {
		if (s == null) {
			out = s;
			return;
		}
		if (s.equals(out)) return;
		out = s;
		for (Vertex v = io[0]; v != null; v = v.next)
			c.updateNode(v.dst, v.pin);
	}

	public void replaceWith(Node node, Context c) {
		for (Vertex v; (v = io[0]) != null;)
			v.dst.connect(v.pin - 1, node, c);
	}

	public void remove(Context c) {
		idx = -1;
		updating = 1;
		replaceWith(null, c);
		if (data instanceof Macro m) {
			m.getOutput(c).replaceWith(null, c);
			for (int i = 1; i < io.length; i++) {
				Node n = io[i].src;
				if (n == null) continue;
				for (Vertex v = n.io[0]; v != null; v = v.next)
					if (v.dst.macro == m)
						v.dst.connect(v.pin - 1, null, c);
			}
		}
		for (int i = 1; i < io.length; i++)
			connect(i - 1, null, c);
	}

	public void clearError() {
		if (error == null) return;
		error.remove();
		error = null;
	}

	public Node clearUpdate() {
		Node n = nextUpdate;
		updating = 0;
		nextUpdate = null;
		return n;
	}

	public boolean needsCompile(int pass) {
		return compiled != (compiled = pass);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(def.name);
		sb.append(" #").append(idx).append('(');
		for (int i = 1; i < io.length; i++)
			(i == 1 ? sb : sb.append(", #")).append(io[i].src.idx);
		return sb.append(')').toString();
	}

	private class Vertex {

		private Vertex next;
		private Node src;
		private final Node dst;
		private final int pin;

		public Vertex(Node dst, int pin) {
			this.dst = dst;
			this.pin = pin;
			dst.io[pin] = this;
		}

	}

}
