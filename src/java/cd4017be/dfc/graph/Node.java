package cd4017be.dfc.graph;

import cd4017be.dfc.compiler.NodeCompiler;
import cd4017be.dfc.graph.Macro.Pin;
import cd4017be.dfc.lang.*;

/**
 * 
 * @author CD4017BE */
public class Node {

	public static final Node NULL = new Node(null, -1, new BlockDef(null, 1, 0, false));
	static {
		NULL.def.behavior = Behavior.NULL;
		NULL.def.compiler = NodeCompiler.NULL;
		NULL.out[0] = Signal.NULL;
	}
	public static final Pin NULL_PIN = new Pin(NULL, 0);

	public final BlockDef def;
	final Vertex[] io;
	public final Macro macro;
	public int idx;
	public Object data;
	public final Signal[] out;
	public SignalError error;
	Node nextUpdate;
	int updating, compiled;

	public Node(Macro macro, int idx, BlockDef def) {
		this.macro = macro;
		this.idx = idx;
		this.def = def;
		this.out = new Signal[def.outCount + def.addOut];
		this.io = new Vertex[def.inCount + def.addIn + out.length];
		for (int i = out.length; i < io.length; i++)
			new Vertex(this, i);
	}

	/**Connect the given input to the given node.
	 * @param i pin index
	 * @param src the source node to connect
	 * @param j source output index
	 * @param c the current context */
	public void connect(int i, Node src, int j, Context c) {
		Vertex in = io[i];
		if (src != null && in.src == src && in.sPin == j) return;
		if (in.src != null) {
			Vertex v = in.src.io[in.sPin];
			if (v == in) in.src.io[in.sPin] = in.next;
			else for (Vertex u; v != null; v = u)
				if ((u = v.next) == in) {
					v.next = in.next;
					break;
				}
		}
		in.src = src;
		in.sPin = j;
		if (src != null && src != NULL) {
			in.next = src.io[j];
			src.io[j] = in;
		} else in.next = null;
		if (src == null || src.out[j] != null)
			c.updateNode(this, i);
	}

	/**@param min minimum argument count
	 * @return the argument list of this node (length >= min)*/
	public String[] arguments(int min) {
		return macro.arguments(this, min);
	}

	/**@param i input index
	 * @return the node currently connected to input i (may be null) */
	public Vertex input(int i) {
		return io[i + out.length];
	}

	/**@param i input index
	 * @param c the current context
	 * @return the signal connected (possibly during this call) to input i. */
	public Signal getInput(int i, Context c) {
		Vertex v = connectIn(i, c);
		return v.src.out[v.sPin];
	}

	public Vertex connectIn(int i, Context c) {
		Vertex v = io[i + out.length];
		if (v.src == null)
			macro.connectInput(this, i, c);
		return v;
	}

	public void disconnectExtraInputs(Context c) {
		for (int i = def.addIn, j = io.length - 1; --i >= 0; j--)
			connect(j, null, 0, c);
	}

	public void updateOutput(int i, Signal s, Context c) {
		if (out[i] == (out[i] = s) || s == null) return;
		for (Vertex v = io[i]; v != null; v = v.next)
			c.updateNode(v.dst, v.dPin);
	}

	public void updateChngOutput(int i, Signal s, Context c) {
		if (s == null) {
			out[i] = s;
			return;
		}
		if (s.equals(out[i])) return;
		out[i] = s;
		for (Vertex v = io[i]; v != null; v = v.next)
			c.updateNode(v.dst, v.dPin);
	}

	public void replaceOut(int i, Node node, int j, Context c) {
		for (Vertex v; (v = io[i]) != null;)
			v.dst.connect(v.dPin, node, j, c);
	}

	public void remove(Context c) {
		idx = -1;
		updating = 1;
		for (int i = 0; i < out.length; i++)
			replaceOut(i, null, 0, c);
		if (data instanceof Macro m) {
			for (int i = 0; i < out.length; i++) {
				Pin pin = m.getOutput(i, c);
				pin.node().replaceOut(pin.pin(), null, 0, c);
			}
			for (int i = out.length; i < io.length; i++) {
				Vertex in = io[i];
				if (in.src == null) continue;
				for (Vertex v = in.src.io[in.sPin]; v != null; v = v.next)
					if (v.dst.macro == m)
						v.dst.connect(v.dPin, null, 0, c);
			}
		}
		for (int i = out.length; i < io.length; i++)
			connect(i, null, 0, c);
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

	public class Vertex {

		private Vertex next;
		public Node src;
		public int sPin;
		private final Node dst;
		private final int dPin;

		public Vertex(Node dst, int dPin) {
			this.dst = dst;
			this.dPin = dPin;
			dst.io[dPin] = this;
		}

		public Pin srcPin() {
			return new Pin(src, sPin);
		}

		public Signal signal() {
			return src.out[sPin];
		}

	}

}
