package cd4017be.dfc.editor;

import static java.lang.Math.min;

import java.nio.ByteBuffer;

import cd4017be.dfc.lang.Signal;

/**Represents a data trace node.
 * @author CD4017BE */
public class Trace implements IMovable {

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
			cc.updatePos(bufOfs, x, y);
		if (to != null)
			for (Trace t0 = to, t = t0.adj; t != null; t0 = t, t = t.adj)
				cc.updatePos(t0.bufOfs - Shaders.TRACE_STRIDE, x, y);
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
	}

	@Override
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

	@Override
	public boolean inRange(int x0, int y0, int x1, int y1) { 
		int x = x(), y = y();
		return x < x1 && y < y1 && x > x0 && y > y0;
	}

}
