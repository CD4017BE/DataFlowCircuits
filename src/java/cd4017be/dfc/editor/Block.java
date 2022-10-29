package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.BLOCK_STRIDE;
import static cd4017be.dfc.editor.Shaders.drawBlock;
import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.graph.Macro;
import cd4017be.dfc.graph.Macro.Pin;
import cd4017be.dfc.graph.Node;
import cd4017be.dfc.lang.*;
import cd4017be.util.IndexedSet;

/**Represents an operand block.
 * @author CD4017BE */
public class Block extends IndexedSet.Element implements IMovable {

	public final BlockDef def;
	public final Trace[] io;
	private String data = "";
	public Node node;
	public short x, y;

	public Block(BlockDef def, CircuitEditor cc) {
		this.def = def;
		this.io = new Trace[def.ios()];
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
		if (def.addOut > 0) {
			var out = circuit().outputs;
			out.remove(data, io[0]);
			out.put(s, io[0]);
		}
		data = s;
	}

	public void redraw() {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			int sx = def.stretch(data);
			circuit().blockVAO.set(getIdx() * BLOCK_STRIDE * 4, drawBlock(ms.malloc(BLOCK_STRIDE * 4),
				x, y, def.icon.w + sx, def.icon.h, sx, 0, def.icon.id
			).flip());
		}
		Main.refresh(0);
	}

	@Override
	public void setIdx(int idx) {
		super.setIdx(idx);
		CircuitEditor cc = circuit();
		if (idx < 0) { // disconnect all Wires on removal
			for (Trace tr : io) tr.remove();
			cc.fullRedraw();
			if (node != null) node.remove(cc.context);
		} else {
			redraw();
			if (node != null) node.idx = idx;
			else cc.createNode(this);
		}
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
		if (node == null) return null;
		if (node.data instanceof Macro m) {
			Pin pin = m.getOutput(i, circuit().context);
			return pin.node().out[pin.pin()];
		}
		return node.out[i];
	}

}
