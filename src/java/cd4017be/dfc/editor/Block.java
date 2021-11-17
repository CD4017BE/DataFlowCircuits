package cd4017be.dfc.editor;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.nio.ByteBuffer;
import java.util.Arrays;

import cd4017be.dfc.lang.*;
import cd4017be.util.AtlasSprite;
import cd4017be.util.IndexedSet;

/**Represents an operand block.
 * @author CD4017BE */
public class Block extends IndexedSet.Element {

	public final BlockDef def;
	public final Trace[] io;
	public String data = "";
	public Signal[] outType = Signal.DEAD_CODE;
	public byte x, y;
	public byte textIdx;
	public int textOfs = -1, posOfs = -1;

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
		int t = def.textSize;
		return t >= 0 ? def.icon.w :
			(t & 15) + (t >> 4 & 15) + max(data.length() - (t >> 12 & 7), 0);
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
		if (!data.isEmpty())
			cc.updateTextPos(textIdx, textPos());
		return this;
	}

	public int textPos() {
		int t = def.textSize;
		return (x + (t >> 4 & 15)) * 4 - min(t >> 12 & 7, data.length()) * 2 & 0xffff
			| ((y + (t >> 8 & 15)) * 4 & 0xffff) << 16;
	}

	public Block place() {
		for (Trace tr : io) tr.place();
		return this;
	}

	public void draw(ByteBuffer buf) {
		int t = def.textSize;
		AtlasSprite s = def.icon;
		buf.put(x).put(y).put((byte)s.x).put((byte)s.y);
		if (t >= 0) buf.put((byte)(s.w | s.h << 4));
		else {
			int n = max(data.length() - (t >> 12 & 7), 0);
			int l = (t >> 4 & 15) + (n >> 1), r = (t & 15) + (n >> 1);
			if (l < r) l += n & 1; else r += n & 1;
			buf.put((byte)(l | s.h << 4));
			buf.put((byte)(x + l)).put(y).put((byte)(s.x + s.w - r)).put((byte)s.y);
			buf.put((byte)(r | s.h << 4));
		}
	}

	@Override
	public void setIdx(int idx) {
		if (idx < 0) { // disconnect all Wires on removal
			for (Trace tr : io) tr.remove();
			io[0].cc.fullRedraw();
		}
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
