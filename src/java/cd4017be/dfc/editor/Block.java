package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;

import org.lwjgl.system.MemoryStack;

import cd4017be.compiler.*;
import cd4017be.util.IndexedSet;

/**Represents an operand block.
 * @author CD4017BE */
public class Block extends IndexedSet.Element implements IMovable {

	public final BlockDef def;
	public final Trace[] io;
	public final int[] nodesIn;
	public final String[] args;
	public final int outs;
	public short x, y, w, h;

	public Block(BlockInfo info, CircuitEditor cc) {
		this(info.def, info.outs, info.ins.length, info.args, cc);
	}

	public Block(BlockDef def, int size, CircuitEditor cc) {
		this(def, def.outs(size), def.ins(size), new String[def.args(size)], cc);
	}

	public Block(BlockDef def, int outs, int ins, String[] args, CircuitEditor cc) {
		this.def = def;
		this.outs = outs;
		this.nodesIn = new int[ins];
		this.io = new Trace[outs + ins];
		this.args = args;
		for (int i = 0; i < io.length; i++)
			io[i] = new Trace(cc, this, i);
		def.model.loadIcon();
		updateSize();
		cc.blocks.add(this);
		cc.fullRedraw();
	}

	public int ins() {
		return nodesIn.length;
	}

	public CircuitEditor circuit() {
		return io[0].cc;
	}

	public boolean isInside(int x, int y) {
		return (x -= this.x) >= 0 && x < w
			&& (y -= this.y) >= 0 && y < h;
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
		updatePins();
		return this;
	}

	public void updateSize() {
		BlockModel model = def.model;
		int w = 0, h = 0;
		for (String s : args) {
			w = max(w, s.length());
			h += 2;
		}
		w += model.tw;
		h += model.th;
		int n = max(0, max(outs * 2 - model.outs.length, ins() * 2 - model.ins.length));
		this.w = (short)max(w, model.icon.w);
		this.h = (short)max(h, model.icon.h + n);
	}

	public void updatePins() {
		BlockModel model = def.model;
		for (int i = 0; i < outs; i++)
			io[i].movePin(i, model.outs, x, y, w, h);
		for (int i = outs, j = 0; i < io.length; i++, j++)
			io[i].movePin(j, model.ins, x, y, w, h);
	}

	public String text() {
		return args[0];
	}

	@Override
	public Block place() {
		for (Trace tr : io) tr.place();
		return this;
	}

	public void setText(String s) {
		args[0] = s;
		//TODO recreate block
	}

	public void redraw() {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			circuit().blockVAO.set(getIdx() * BLOCK_STRIDE * 4, drawBlock(
				ms.malloc(BLOCK_STRIDE * 4),
				x, y, w, h, def.model.icon
			).flip());
		}
		Main.refresh(0);
	}

	public int textX() {
		return x * 2 + w + def.model.tx;
	}

	public int textY() {
		return y * 2 + def.model.ty;
	}

	public void printText() {
		if (args.length == 0) return;
		int x = textX();
		int y = textY();
		for (String s : args) {
			if (!s.isBlank())
				print(s, FG_YELLOW_L, x - s.length(), y, 2, 3);
			y += 4;
		}
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
		return x < x1 && y < y1 && x + w > x0 && y + h > y0;
	}

}
