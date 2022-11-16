package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.BLOCK_STRIDE;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL32C.*;

import cd4017be.dfc.lang.*;
import cd4017be.util.IndexedSet;

/**Represents an operand block.
 * @author CD4017BE */
public class Block extends IndexedSet.Element implements IMovable {

	public final BlockDef def;
	public final Trace[] io;
	public final int[] nodesIn;
	private String data = "";
	public short x, y;

	public Block(BlockDef def, CircuitEditor cc) {
		this.def = def;
		this.io = new Trace[def.ios()];
		this.nodesIn = new int[def.inCount];
		for (int i = 0; i < io.length; i++)
			io[i] = new Trace(cc, this, i);
		cc.icons.load(def, cc.reg);
		cc.blocks.add(this);
		cc.fullRedraw();
	}

	public CircuitEditor circuit() {
		return io[0].cc;
	}

	public int w() {
		return def.icon.w + def.stretch(data);
	}

	public int h() {
		return def.icon.h;
	}

	public boolean isInside(int x, int y) {
		return (x -= this.x) >= 0 && x < w()
			&& (y -= this.y) >= 0 && y < h();
	}

	@Override
	public Block pickup() {
		for (Trace tr : io) tr.pickup();
		return this;
	}

	@Override
	public Block pos(int x, int y) {
		this.x = (short)x;
		this.y = (short)y;
		redraw();
		byte[] ports = def.pins;
		int w = w() + 1;
		for (int i = 0, j = 0; i < io.length; i++) {
			int dx = ports[j++];
			io[i].pos(x + (dx < 0 ? dx + w : dx), y + ports[j++]);
		}
		return this;
	}

	public int textX() {
		return x * 4 + def.textX(data) * 2;
	}

	public int textY() {
		return y * 4 + def.textY() * 2 + 1;
	}

	public String text() {
		return data;
	}

	@Override
	public Block place() {
		for (Trace tr : io) tr.place();
		return this;
	}

	public void setText(String s) {
		data = s;
		//TODO recreate block
	}

	public void redraw() {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(BLOCK_STRIDE);
			buf.putShort(x).putShort(y)
			.put((byte)def.stretch(data))
			.put((byte)0).putShort((short)def.icon.id);
			glBindBuffer(GL_ARRAY_BUFFER, circuit().blockBuf);
			glBufferSubData(GL_ARRAY_BUFFER, getIdx() * BLOCK_STRIDE, buf.flip());
		}
		Main.refresh(0);
	}

	@Override
	public void setIdx(int idx) {
		CircuitEditor cc = circuit();
		if (idx < 0) { // disconnect all Wires on removal
			for (Trace tr : io) tr.remove();
			cc.macro.removeBlock(this);
			cc.fullRedraw();
		} else {
			if (getIdx() < 0)
				cc.macro.addBlock(this);
			redraw();
		}
		super.setIdx(idx);
	}

	@Override
	public String toString() {
		return String.format("#%d = %s", getIdx(), def);
	}

	public String id() {
		return def.name;
	}

	@Override
	public short x() { 
		return x;
	}

	@Override
	public short y() { 
		return y;
	}

	@Override
	public void remove() {
		circuit().blocks.remove(this);
	}

	@Override
	public boolean inRange(int x0, int y0, int x1, int y1) { 
		return x < x1 && y < y1 && x + w() > x0 && y + h() > y0;
	}

	public Signal signal(int i) {
		return null;
		//TODO
	}

}
