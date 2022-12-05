package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.*;
import org.lwjgl.system.MemoryStack;

import cd4017be.compiler.*;
import cd4017be.util.IndexedSet;
import cd4017be.util.VertexArray;

/**Represents a data trace node.
 * @author CD4017BE */
public class Trace extends IndexedSet.Element implements CircuitObject {

	public Block block;
	public Trace from, to, adj;
	/**-1: trace, 0..outs-1: output, outs..: input */
	public final int pin;
	private int node, color = VOID_COLOR;
	private short x, y;
	private VertexArray va;

	public Trace() {
		this(null, -1);
	}

	Trace(Block block, int pin) {
		this.block = block;
		this.pin = pin;
	}

	public boolean isOut() {
		return pin >= 0 && pin < block.outs;
	}

	public int node() {
		return node;
	}

	@Override
	public short x() {
		return x;
	}

	@Override
	public short y() {
		return y;
	}

	public void movePin(int i, byte[] pins, int x, int y, int w, int h, int rh, CircuitEditor cc) {
		int o = i - (pins.length >> 1) + 1;
		if (o > 0) {
			y += o * rh;
			i -= o;
		}
		int dx = pins[i*=2], dy = pins[i+1];
		if (dx < 0) dx += w + 1;
		if (dy < 0) dy += h + 1;
		pos(x + dx, y + dy, cc);
	}

	@Override
	public Trace pos(int x, int y, CircuitEditor cc) {
		this.x = (short)x;
		this.y = (short)y;
		draw();
		for (Trace t = to; t != null; t = t.adj)
			t.draw();
		return this;
	}

	@Override
	public Trace pickup(CircuitEditor cc) {
		return this;
	}

	@Override
	public Trace place(CircuitEditor cc) {
		for (Block block : cc.blocks)
			for (int i = 0; i < block.outs; i++) {
				Trace tr = block.io[i];
				if (tr.x == x && tr.y == y && tr != this)
					return merge(tr, cc);
			}
		for (Trace tr : cc.traces)
			if (tr.x == x && tr.y == y && tr != this)
				return merge(tr, cc);
		return this;
	}

	private Trace merge(Trace tr, CircuitEditor cc) {
		Trace rem;
		if (this.pin < 0)
			rem = this;
		else if (tr.pin < 0) {
			rem = tr;
			tr = this;
		} else {
			if (!tr.isOut()) {
				rem = tr;
				tr = this;
			} else if (!this.isOut())
				rem = this;
			else {
				Block block0 = tr.block, block1 = this.block;
				Trace[] io0 = block0.io, io1 = block1.io;
				for (Trace out = tr; out.to != null;)
					out.to.connect(this, cc);
				for (int i = block0.outs, j = block1.outs; i < io0.length && j < io1.length; i++, j++)
					io1[j].connect(io0[i].from, cc);
				tr.block.remove(cc);
				return this;
			}
			rem.connect(tr, cc);
			return tr;
		}
		rem.connect(tr, cc);
		rem.remove(cc);
		return tr;
	}

	public void connect(Trace tr, CircuitEditor cc) {
		if (from == tr) return;
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
		draw();
	}

	public void setNode(int node, CircuitEditor cc) {
		if (node == this.node) return;
		this.node = node;
		if (block != null) {
			int in = pin - block.outs;
			if (in >= 0)
				cc.macro.connect(node, block.nodesIn[in]);
		}
		for (Trace to = this.to; to != null; to = to.adj)
			cc.traceUpdates.add(to);
	}

	public void update(CircuitEditor cc) {
		if (!isOut()) setNode(from == null ? -1 : from.node, cc);
	}

	@Override
	public void add(CircuitEditor cc) {
		if (!isOut()) {
			va = cc.traceVAO;
			cc.traces.add(this);
		}
	}

	@Override
	public void remove(CircuitEditor cc) {
		va = null;
		cc.traces.remove(this);
		pickup(cc);
		while(to != null) to.connect(from, cc);
		connect(null, cc);
	}

	@Override
	public void draw() {
		int idx = getIdx();
		if (idx < 0) return;
		Trace from = this.from;
		if (from == null) from = this;
		try (MemoryStack ms = MemoryStack.stackPush()) {
			va.set(idx * 4, drawTrace(
				ms.malloc(TRACE_PRIMLEN),
				from.x, from.y, x, y, color
			).flip());
		}
		Main.refresh(0);
	}

	@Override
	public void setIdx(int idx) {
		super.setIdx(idx);
		draw();
	}

	public static final short VOID_COLOR = 0;

	public static int key(int x, int y) {
		return y << 16 | x & 0xffff;
	}

	@Override
	public boolean inRange(int x0, int y0, int x1, int y1) { 
		int x = x(), y = y();
		return x < x1 && y < y1 && x > x0 && y > y0;
	}

	public void updateColor(MacroState ms) {
		Value s = value(ms);
		int c = s == null ? VOID_COLOR : s.type.vtable.color;
		if (c != color) {
			color = c;
			draw();
		}
	}

	public Value value(MacroState ms) {
		if (node < 0 || ms == null) return null;
		NodeState ns = ms.states[node];
		return ns == null ? null : ns.value;
	}

}
