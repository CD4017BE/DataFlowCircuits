package cd4017be.dfc.editor;

import java.nio.ByteBuffer;

import cd4017be.dfc.lang.Signal;

/**Represents a data trace node.
 * @author CD4017BE */
public class Trace {

	public final Circuit cc;
	public Block block;
	public Trace from, to, adj;
	/**-1: trace, 0: output, 1..: input */
	public final int pin;
	private int pos, bufOfs;
	public boolean placed;

	public Trace(Circuit cc) {
		this(cc, null, -1);
	}

	Trace(Circuit cc, Block block, int pin) {
		this.cc = cc;
		this.block = block;
		this.pin = pin;
	}

	public short x() {
		return (short)pos;
	}

	public short y() {
		return (short)(pos >> 16);
	}

	public Trace pos(int x, int y) {
		if (placed) throw new IllegalStateException("must pickup before move");
		pos = key(x, y);
		if (from != null || to != null)
			cc.updatePos(bufOfs, x, y);
		if (to != null)
			for (Trace t0 = to, t = t0.adj; t != null; t0 = t, t = t.adj)
				cc.updatePos(t0.bufOfs - Shaders.TRACE_STRIDE, x, y);
		return this;
	}

	public Trace pickup() {
		if (!placed) return this;
		cc.traces.remove(pos, this);
		placed = false;
		if (pin >= 0)
			for (Trace t = to; t != null; t = t.adj)
				if (t.pos == pos) t.place();
		return this;
	}

	public Trace place() {
		if (placed) return this;
		placed = true;
		return cc.traces.merge(pos, this, Trace::merge);
	}

	private Trace merge(Trace tr) {
		Trace rem;
		if (tr.pin < 0) {
			rem = tr;
			tr = this;
		} else if (pin < 0) rem = this;
		else {
			if (tr.pin > 0) {
				rem = tr;
				tr = this;
			} else if (pin > 0) rem = this;
			else throw new IllegalStateException("output pin collision");
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
	}

	public void remove() {
		pickup();
		while(to != null) to.connect(from);
		connect(null);
	}

	public void draw(ByteBuffer buf, boolean start) {
		Block block = null;
		for(Trace tr = from; tr != null; tr = tr.from)
			if(tr.pin <= 0) {
				block = tr.block;
				break;
			}
		if (pin < 0) this.block = block;
		bufOfs = buf.position();
		buf.putInt(pos).putShort(
			start ? STOP_COLOR :
			block == null || block.outType == Signal.NULL ? VOID_COLOR :
			(short)block.outType.type.color(block.outType)
		);
	}

	public static final short VOID_COLOR = 47, STOP_COLOR = 0;

	public static int key(int x, int y) {
		return y << 16 | x & 0xffff;
	}

}
