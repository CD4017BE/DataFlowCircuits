package cd4017be.dfc.editor.circuit;

import static cd4017be.dfc.editor.Shaders.*;

import java.util.ArrayList;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryStack;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.Node.Vertex;
import cd4017be.util.IndexedSet;

/**Represents a data trace node.
 * @author CD4017BE */
public class Trace extends IndexedSet.Element implements CircuitObject {

	public final Block block;
	public Trace from, to, adj, src;
	/**-1: trace, 0..outs-1: output, outs..: input */
	public final int pin;
	private short x, y;
	private CircuitEditor ce;

	public Trace() {
		this.block = null;
		this.pin = -1;
	}

	Trace(Block block, int pin) {
		this.block = block;
		this.pin = pin;
		this.src = pin < block.outs() ? this : null;
	}

	public boolean isOut() {
		return src == this;
	}

	@Override
	public short x() {
		return x;
	}

	@Override
	public short y() {
		return y;
	}

	public void movePin(int i, short[] pins, int x, int y, int w, int h, int rh) {
		int o = i - (pins.length) + 1;
		if (o > 0) {
			y += o * rh;
			i -= o;
		}
		int dx = i < 0 ? 0 : pins[i];
		int dy = dx >> 8; dx = (byte)dx;
		if (dx < 0) dx += w + 1;
		if (dy < 0) dy += h + 1;
		pos(x + dx, y + dy);
	}

	@Override
	public Trace pos(int x, int y) {
		this.x = (short)x;
		this.y = (short)y;
		draw();
		for (Trace t = to; t != null; t = t.adj)
			t.draw();
		return this;
	}

	@Override
	public Trace pickup() {
		return this;
	}

	@Override
	public Trace place() {
		for (Block block : ce.blocks)
			for (int i = 0; i < block.outs(); i++) {
				Trace tr = block.io[i];
				if (tr.x == x && tr.y == y && tr != this)
					return merge(tr);
			}
		for (Trace tr : ce.traces)
			if (tr.x == x && tr.y == y && tr != this)
				return merge(tr);
		return this;
	}

	private Trace merge(Trace tr) {
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
					out.to.connect(this);
				for (int i = block0.outs(), j = block1.outs(); i < io0.length && j < io1.length; i++, j++)
					io1[j].connect(io0[i].from);
				tr.block.remove();
				return this;
			}
			rem.connect(tr);
			return tr;
		}
		rem.connect(tr);
		rem.remove();
		return tr;
	}

	public void connect(Trace tr) {
		if (from == tr || isOut()) return;
		if (from != null) {
			Trace t0 = null;
			for (Trace t = from.to; t != this; t = t.adj) t0 = t;
			if (t0 == null)
				from.to = adj;
			else t0.adj = adj;
		}
		if (tr == this) tr = null;
		from = tr;
		Trace src;
		if (tr != null) {
			adj = tr.to;
			tr.to = this;
			src = tr.src;
		} else src = null;
		if (this.src != src) {
			ArrayList<Trace> stack = new ArrayList<>();
			stack.add(this);
			forEachUser(stack, trace -> trace.updateSrc(src));
		}
		draw();
	}

	private void updateSrc(Trace src) {
		this.src = src;
		draw();
		if (pin < 0) return;
		int i = pin - block.outs();
		if (src != null)
			block.connectIn(i, src.block, src.pin);
		else block.connectIn(i, null, -1);
		Vertex v = block.ins[i];
		if (v != null && v.scope() != null)
			ce.reRunTypecheck = true;
	}

	@Override
	public void add(CircuitEditor cb) {
		this.ce = cb;
		if (!isOut()) cb.traces.add(this);
	}

	@Override
	public void remove() {
		ce.traces.remove(this);
		pickup();
		while(to != null) to.connect(from);
		connect(null);
		this.ce = null;
	}

	@Override
	public void draw() {
		int idx = getIdx();
		if (idx < 0) return;
		Trace from = this.from;
		if (from == null) from = this;
		try (MemoryStack ms = MemoryStack.stackPush()) {
			ce.traceVAO.set(idx * 4, drawTrace(
				ms.malloc(TRACE_PRIMLEN),
				from.x, from.y, x, y,
				src == null ? 1 : src.block.colors[src.pin]
			).flip());
		}
		ce.markDirty();
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

	public Value value() {
		return src == null ? null : src.block.signal(src.pin);
	}

	public static void forEachUser(ArrayList<Trace> stack, Consumer<Trace> op) {
		while(!stack.isEmpty()) {
			Trace tr = stack.remove(stack.size() - 1);
			op.accept(tr);
			for (tr = tr.to; tr != null; tr = tr.adj)
				stack.add(tr);
		}
	}

}
