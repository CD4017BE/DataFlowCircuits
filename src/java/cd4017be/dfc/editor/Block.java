package cd4017be.dfc.editor;

import static java.lang.Math.max;
import java.nio.ByteBuffer;
import java.util.Arrays;

import cd4017be.dfc.lang.*;
import cd4017be.util.IndexedSet;

/**Represents an operand block.
 * @author CD4017BE */
public class Block extends IndexedSet.Element {

	public final BlockDef def;
	public final Trace[] io;
	public String data = "";
	public Signal[] outType = Signal.DEAD_CODE;
	public byte x, y;

	public Block(String id, Circuit cc) {
		this(cc.icons.get(id), cc);
	}

	public Block(BlockDef def, Circuit cc) {
		this.def = def;
		this.io = new Trace[def.ios()];
		for (int i = 0; i < io.length; i++)
			io[i] = new Trace(cc, this, i);
		cc.blocks.add(this);
		cc.fullRedraw();
	}

	public int w() {
		return def.icon.w + max(0, data.length() - def.textL0);
	}

	public int h() {
		return def.icon.h;
	}

	public boolean isInside(int x, int y) {
		return (x -= this.x) >= 0 && x < w()
			&& (y -= this.y) >= 0 && y < h();
	}

	public Block pickup() {
		for (Trace tr : io) tr.pickup();
		return this;
	}

	public Block pos(int x, int y) {
		this.x = (byte)x;
		this.y = (byte)y;
		Circuit cc = io[0].cc;
		cc.redrawBlock(this);
		byte[] ports = def.ports;
		int w = w() + 1;
		for (int i = 0, j = 0; i < io.length; i++) {
			int dx = ports[j++];
			io[i].pos(x + (dx < 0 ? dx + w : dx), y + ports[j++]);
		}
		return this;
	}

	public int textX() {
		return x * 4 + (def.textX + max(0, def.textL0 - data.length())) * 2;
	}

	public int textY() {
		return y * 4 + def.textY * 2;
	}

	public Block place() {
		for (Trace tr : io) tr.place();
		return this;
	}

	public void draw(ByteBuffer buf) {
		buf.put(x).put(y)
		.put((byte)max(0, data.length() - def.textL0))
		.put((byte)def.icon.id);
	}

	@Override
	public void setIdx(int idx) {
		if (idx < 0) { // disconnect all Wires on removal
			for (Trace tr : io) tr.remove();
			io[0].cc.fullRedraw();
		}
		//TODO redraw here
		super.setIdx(idx);
	}

	@Override
	public String toString() {
		String s = Arrays.toString(io);
		return String.format("#%d = %s(%s)", getIdx(), def, s.substring(1, s.length() - 1));
	}

	public String id() {
		return def.name;
	}

}
