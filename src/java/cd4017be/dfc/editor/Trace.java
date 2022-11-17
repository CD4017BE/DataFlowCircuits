package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.TRACE_STRIDE;
import static cd4017be.dfc.editor.Shaders.drawTrace;
import static java.lang.Math.min;

import org.lwjgl.system.MemoryStack;
import cd4017be.compiler.MacroState;
import cd4017be.compiler.NodeState;
import cd4017be.compiler.Signal;
import cd4017be.util.VertexArray;

/**Represents a data trace node.
 * @author CD4017BE */
public class Trace implements IMovable {

	public final CircuitEditor cc;
	public Block block;
	public Trace from, to, adj;
	/**-1: trace, 0: output, 1..: input */
	public final int pin;
	private int node;
	private int pos, bufOfs;
	public boolean placed;

	public Trace(CircuitEditor cc) {
		this(cc, null, -1);
	}

	Trace(CircuitEditor cc, Block block, int pin) {
		this.cc = cc;
		this.block = block;
		this.pin = pin;
	}

	public boolean isOut() {
		return pin >= 0 && pin < block.def.outCount;
	}

	public int node() {
		return node;
	}

	@Override
	public short x() {
		return (short)pos;
	}

	@Override
	public short y() {
		return (short)(pos >> 16);
	}

	@Override
	public Trace pos(int x, int y) {
		if (placed) throw new IllegalStateException("must pickup before move");
		pos = key(x, y);
		if (from != null || to != null)
			cc.updatePos(this);
		for (Trace t = to; t != null; t = t.adj)
				cc.updatePos(this);
		return this;
	}

	@Override
	public Trace pickup() {
		if (!placed) return this;
		cc.traces.remove(pos, this);
		placed = false;
		if (pin >= 0)
			for (Trace t = to; t != null; t = t.adj)
				if (t.pos == pos) t.place();
		return this;
	}

	@Override
	public Trace place() {
		if (placed) return this;
		try {
			placed = true;
			return cc.traces.merge(pos, this, Trace::merge);
		} catch (MergeConflict e) {
			placed = false;
			Trace[] io0 = e.conflict.block.io, io1 = block.io;
			for (Trace out = io0[0]; out.to != null;)
				out.to.connect(this);
			for (int i = min(io0.length, io1.length) - 1; i > 0; i--)
				io1[i].connect(io0[i].from);
			e.conflict.block.remove();
			return place();
		}
	}

	private Trace merge(Trace tr) {
		Trace rem;
		if (tr.pin < 0) {
			rem = tr;
			tr = this;
		} else if (this.pin < 0)
			rem = this;
		else {
			if (tr.pin > 0) {
				rem = tr;
				tr = this;
			} else if (this.pin > 0)
				rem = this;
			else throw new MergeConflict(this);
			rem.connect(tr);
			rem.placed = false;
			return tr;
		}
		while(rem.to != null) rem.to.connect(tr);
		rem.connect(null);
		rem.placed = false;
		cc.fullRedraw();
		return tr;
	}

	@SuppressWarnings("serial")
	private static class MergeConflict extends IllegalStateException {
		final Trace conflict;
		MergeConflict(Trace collision) {
			this.conflict = collision;
		}
	}

	public void connect(Trace tr) {
		if (from == tr) return;
		cc.fullRedraw();
		if (from != null) {
			Trace t0 = null;
			for (Trace t = from.to; t != this; t = t.adj) t0 = t;
			if (t0 == null)
				from.to = adj;
			else t0.adj = adj;
		}
		if (tr == this) tr = null;
		from = tr;
		if (tr != null) {
			adj = tr.to;
			tr.to = this;
		}
		cc.traceUpdates.add(this);
	}

	public void setNode(int node) {
		if (node == this.node) return;
		this.node = node;
		if (block != null) {
			int in = pin - block.def.outCount;
			if (in >= 0)
				cc.macro.connect(node, block.nodesIn[in]);
		}
		for (Trace to = this.to; to != null; to = to.adj)
			cc.traceUpdates.add(to);
	}

	public void update() {
		if (!isOut()) setNode(from == null ? -1 : from.node);
	}

	@Override
	public void remove() {
		pickup();
		while(to != null) to.connect(from);
		connect(null);
	}

	public void draw(VertexArray va, MacroState ms, boolean re) {
		if (from == null) return;
		if (re || bufOfs < 0) bufOfs = va.count;
		NodeState ns = ms != null && node >= 0 ? ms.states[node] : null;
		Signal s = ns != null ? ns.signal : null;
		try (MemoryStack mem = MemoryStack.stackPush()) {
			va.set(bufOfs, drawTrace(mem.malloc(TRACE_STRIDE * 4), from.x(), from.y(), x(), y(), s == null ? VOID_COLOR : s.type.n));
		}
	}

	public static final short VOID_COLOR = 47, STOP_COLOR = 0;

	public static int key(int x, int y) {
		return y << 16 | x & 0xffff;
	}

	@Override
	public boolean inRange(int x0, int y0, int x1, int y1) { 
		int x = x(), y = y();
		return x < x1 && y < y1 && x > x0 && y > y0;
	}

}
