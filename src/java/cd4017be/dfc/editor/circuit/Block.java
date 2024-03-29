package cd4017be.dfc.editor.circuit;

import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.ArrayList;
import java.util.Arrays;

import org.lwjgl.system.MemoryStack;
import cd4017be.dfc.graphics.SpriteModel;
import cd4017be.dfc.lang.*;
import modules.dfc.module.Intrinsics;

/**Represents an operand block.
 * @author CD4017BE */
public class Block extends BlockDesc implements CircuitObject {

	public final Trace[] io;
	protected final short[] colors;
	public short x, y, w, h;
	private CircuitEditor ce;

	public Block(BlockDesc desc) {
		this(desc.def, desc.outs.length, desc.ins.length, desc.args);
	}

	public Block(BlockDef def, int size) {
		this(def, def.outs(size), def.ins(size), new String[def.args(size)]);
	}

	public Block(BlockDef def, int outs, int ins, String[] args) {
		super(def, outs, new int[ins], args);
		this.io = new Trace[outs + ins];
		this.colors = new short[outs];
		for (int i = 0; i < io.length; i++)
			io[i] = new Trace(this, i);
		for (int i = 0; i < args.length; i++)
			if (args[i] == null) args[i] = "";
		Arrays.fill(colors, Trace.VOID_COLOR);
		def.loadModel();
		updateSize();
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
		draw();
		updatePins();
		return this;
	}

	public Block resize(int ds) {
		if (ds == 0) return this;
		int s = size() + ds;
		if (s < 0)
			if (s == ds) return this;
			else s = 0;
		Block block = new Block(def, s);
		System.arraycopy(args, 0, block.args, 0, min(args.length, block.args.length));
		block.updateSize();
		for (int i = 0, l = min(outs(), block.outs()); i < l; i++) {
			Trace ta = io[i], tb = block.io[i];
			while(ta.to != null) ta.to.connect(tb);
		}
		for (int i = outs(), j = block.outs(), l = min(ins(), block.ins()); l > 0; i++, j++, l--) {
			Trace ta = io[i], tb = block.io[j];
			while(ta.to != null) ta.to.connect(tb);
			tb.connect(ta.from);
		}
		CircuitEditor cb = this.ce;
		remove();
		block.pos(x, y).add(cb);
		return block.place();
	}

	public int size() {
		return max(max(
			outs() - def.outs.length,
			ins() - def.ins.length),
			args.length - def.args.length
		) + 1;
	}

	public void updateSize() {
		SpriteModel model = def.model;
		int w = 0, h = 0;
		for (String s : args) {
			w = max(w, s.length());
			h += 2;
		}
		w += model.tw();
		h += model.th();
		int n = max(-1, max(outs() - model.outs.length, ins() - model.ins.length)) * model.rh();
		this.w = (short)max(w, model.icon.w);
		this.h = (short)max(h, model.icon.h + n);
		draw();
	}

	public void updatePins() {
		SpriteModel model = def.model;
		for (int i = 0; i < outs(); i++)
			io[i].movePin(i, model.outs, x, y, w, h, model.rh());
		for (int i = outs(), j = 0; i < io.length; i++, j++)
			io[i].movePin(j, model.ins, x, y, w, h, model.rh());
	}

	@Override
	public Block place() {
		for (Trace tr : io) tr.place();
		return this;
	}

	@Override
	public void draw() {
		int idx = getIdx();
		if (idx < 0) return;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ce.blockVAO.set(idx * 4, drawBlock(
				ms.malloc(BLOCK_PRIMLEN),
				x, y, w, h, def.model.icon
			).flip());
		}
		ce.markDirty();
	}

	public int textX() {
		return x * 2 + w + def.model.tx();
	}

	public int textY() {
		return y * 2 + def.model.ty();
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
	public void add(CircuitEditor cb) {
		this.ce = cb;
		if (cb.blocks.add(this))
			for (Trace tr : io) tr.add(cb);
		if (outs() == 0) cb.reRunTypecheck = true;
	}

	@Override
	public void remove() {
		ce.blocks.remove(this);
		for (Trace tr : io) tr.remove();
		if (ce.errorBlock == this) {
			ce.errorBlock = null;
			ce.reRunTypecheck = true;
		}
		this.ce = null;
	}

	@Override
	public boolean inRange(int x0, int y0, int x1, int y1) { 
		return x < x1 && y < y1 && x + w > x0 && y + h > y0;
	}

	public void updateColors() {
		ArrayList<Trace> stack = new ArrayList<>();
		for (int i = 0; i < colors.length; i++) {
			Value s = signal(i);
			short c = s == null ? Trace.VOID_COLOR : (short)s.type.color(s);
			if (c != colors[i]) {
				colors[i] = (short)c;
				stack.add(io[i]);
				Trace.forEachUser(stack, Trace::draw);
			}
		}
	}

	@Override
	public Value signal(int pin) {
		Value[] state = ce.result.vars;
		if (state == null) return null;
		Node node;
		if (pin < outs()) node = outs[pin];
		else if ((node = inNode(pin - outs())) == null)
			return Intrinsics.NULL;
		int addr = node == null ? 0 : node.addr(0);
		return addr <= 0 || addr >= state.length ? null : state[addr];
	}

}
