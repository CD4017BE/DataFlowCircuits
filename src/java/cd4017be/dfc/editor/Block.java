package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.Arrays;

import org.lwjgl.system.MemoryStack;

import cd4017be.compiler.*;
import cd4017be.util.IndexedSet;
import cd4017be.util.VertexArray;

/**Represents an operand block.
 * @author CD4017BE */
public class Block extends IndexedSet.Element implements CircuitObject {

	public final BlockDef def;
	public final Trace[] io;
	public final int[] nodesIn;
	public final String[] args;
	public final int outs;
	public short x, y, w, h;
	private VertexArray va;

	public Block(BlockDesc info) {
		this(info.def, info.outs.length, info.ins.length, info.args);
	}

	public Block(BlockDef def, int size) {
		this(def, def.outs(size), def.ins(size), new String[def.args(size)]);
	}

	public Block(BlockDef def, int outs, int ins, String[] args) {
		this.def = def;
		this.outs = outs;
		this.nodesIn = new int[ins];
		this.io = new Trace[outs + ins];
		this.args = args;
		for (int i = 0; i < io.length; i++)
			io[i] = new Trace(this, i);
		for (int i = 0; i < args.length; i++)
			if (args[i] == null) args[i] = "";
		def.model.loadIcon();
		updateSize();
	}

	public int ins() {
		return nodesIn.length;
	}

	public boolean isInside(int x, int y) {
		return (x -= this.x) >= 0 && x < w
			&& (y -= this.y) >= 0 && y < h;
	}

	@Override
	public Block pickup(CircuitEditor cc) {
		for (Trace tr : io) tr.pickup(cc);
		return this;
	}

	@Override
	public Block pos(int x, int y, CircuitEditor cc) {
		this.x = (short)x;
		this.y = (short)y;
		draw();
		updatePins(cc);
		return this;
	}

	public Block resize(int ds, CircuitEditor cc) {
		if (ds == 0) return this;
		int s = size() + ds;
		if (s < 0)
			if (s == ds) return this;
			else s = 0;
		Block block = new Block(def, def.outs(s), def.ins(s), Arrays.copyOf(args, def.args(s)));
		System.arraycopy(args, 0, block.args, 0, min(args.length, block.args.length));
		for (int i = 0, l = min(outs, block.outs); i < l; i++) {
			Trace ta = io[i], tb = block.io[i];
			while(ta.to != null) ta.to.connect(tb, cc);
		}
		for (int i = outs, j = block.outs, l = min(ins(), block.ins()); l > 0; i++, j++, l--) {
			Trace ta = io[i], tb = block.io[j];
			while(ta.to != null) ta.to.connect(tb, cc);
			tb.connect(ta.from, cc);
		}
		remove(cc);
		block.pos(x, y, cc).add(cc);
		return block.place(cc);
	}

	public int size() {
		return max(max(
			outs - def.outs.length,
			ins() - def.ins.length),
			args.length - def.args.length
		) + 1;
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
		int n = max(-1, max(outs * 2 - model.outs.length, ins() * 2 - model.ins.length) >> 1) * model.rh;
		this.w = (short)max(w, model.icon.w);
		this.h = (short)max(h, model.icon.h + n);
		draw();
	}

	public void updatePins(CircuitEditor cc) {
		BlockModel model = def.model;
		for (int i = 0; i < outs; i++)
			io[i].movePin(i, model.outs, x, y, w, h, model.rh, cc);
		for (int i = outs, j = 0; i < io.length; i++, j++)
			io[i].movePin(j, model.ins, x, y, w, h, model.rh, cc);
	}

	@Override
	public Block place(CircuitEditor cc) {
		for (Trace tr : io) tr.place(cc);
		return this;
	}

	@Override
	public void draw() {
		int idx = getIdx();
		if (idx < 0) return;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			va.set(idx * 4, drawBlock(
				ms.malloc(BLOCK_PRIMLEN),
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
		super.setIdx(idx);
		draw();
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
	public void add(CircuitEditor cc) {
		va = cc.blockVAO;
		if (cc.blocks.add(this)) {
			cc.macro.addBlock(this, cc);
			for (Trace tr : io) tr.add(cc);
		}
	}

	@Override
	public void remove(CircuitEditor cc) {
		va = null;
		cc.blocks.remove(this);
		cc.macro.removeBlock(this, cc);
		for (Trace tr : io) tr.remove(cc);
	}

	@Override
	public boolean inRange(int x0, int y0, int x1, int y1) { 
		return x < x1 && y < y1 && x + w > x0 && y + h > y0;
	}

}
