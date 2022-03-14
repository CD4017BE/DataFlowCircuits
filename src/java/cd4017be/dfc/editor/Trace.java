package cd4017be.dfc.editor;

import static java.lang.Math.min;

import java.nio.ByteBuffer;

import cd4017be.dfc.lang.Signal;
import cd4017be.dfc.lang.Type;

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

	public byte x() {
		return (byte)pos;
	}

	public byte y() {
		return (byte)(pos >> 8);
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
		buf.putShort((short)pos).putShort(
			start ? STOP_COLOR :
			block == null ? VOID_COLOR :
			color(block.outType)
		);
	}

	public static short color(Signal[] s) {
		if (s.length == 0) return VOID_COLOR;
		int l = s.length, c = l > 4 ? 0x1000 : 0;
		if (l > 4) l = 3;
		for (int i = 0; i < l; i++) {
			int t = min(s[i].type, Type.POINTER);
			if (t < 0) t = Type.POINTER + 1;
			c |= (t + 1 & 15) << i * 4;
		}
		return (short)c;
	}

	public static final short VOID_COLOR = 15, STOP_COLOR = 0;

	public static Integer key(int x, int y) {
		return Integer.valueOf((y & 0xff) << 8 | x & 0xff);
	}

}
