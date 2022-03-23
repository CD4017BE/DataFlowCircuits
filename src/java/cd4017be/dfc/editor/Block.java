package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.BLOCK_STRIDE;
import static java.lang.Math.max;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL32C.*;

import cd4017be.dfc.lang.*;
import cd4017be.util.IndexedSet;

/**Represents an operand block.
 * @author CD4017BE */
public class Block extends IndexedSet.Element {

	public final BlockDef def;
	public final Trace[] io;
	public String data = "";
	public Signal[] outType = Signal.DEAD_CODE;
	public short x, y;

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
		this.x = (short)x;
		this.y = (short)y;
		redraw();
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
		return y * 4 + def.textY * 2 + 1;
	}

	public Block place() {
		for (Trace tr : io) tr.place();
		return this;
	}

	public void redraw() {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(BLOCK_STRIDE);
			buf.putShort(x).putShort(y)
			.put((byte)max(0, data.length() - def.textL0))
			.put((byte)0).putShort((short)def.icon.id);
			glBindBuffer(GL_ARRAY_BUFFER, io[0].cc.blockBuf);
			glBufferSubData(GL_ARRAY_BUFFER, getIdx() * BLOCK_STRIDE, buf.flip());
		}
		Main.refresh(0);
	}

	@Override
	public void setIdx(int idx) {
		super.setIdx(idx);
		if (idx < 0) { // disconnect all Wires on removal
			for (Trace tr : io) tr.remove();
			io[0].cc.fullRedraw();
		} else redraw();
	}

	@Override
	public String toString() {
		return String.format("#%d = %s", getIdx(), def);
	}

	public String id() {
		return def.name;
	}

}
