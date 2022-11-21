package cd4017be.dfc.editor;

import java.io.*;
import java.util.*;

import cd4017be.compiler.*;
import cd4017be.util.*;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL20C.*;

/**Implements the circuit editor user interface.
 * @author CD4017BE */
public class CircuitEditor implements IGuiSection {

	/**Editing modes */
	private static final byte M_IDLE = 0, M_BLOCK_SEL = 1, M_TRACE_SEL = 2, M_TRACE_MV = 3, M_TRACE_DRAW = 4,
	M_MULTI_SEL = 5, M_MULTI_MOVE = 6;

	public final IndexedSet<Block> blocks;
	public final IndexedSet<Trace> traces;
	public final HashMap<Integer, Trace> traceLookup;
	final ArrayList<IMovable> moving = new ArrayList<>();
	final ArrayDeque<Trace> traceUpdates = new ArrayDeque<>();
	final IconAtlas icons;
	/** GL vertex arrays */
	final VertexArray blockVAO, traceVAO;
	/** mouse grid offset and zoom */
	int ofsX, ofsY, zoom = 16;
	boolean panning = false;
	/** 0: nothing, 1: frame, 2: texts, -1: everything */
	byte redraw;
	/** current editing mode */
	byte mode;
	/** mouse positions */
	int mx = -1, my = -1, lmx, lmy;
	Trace selTr;
	Block selBlock;
	TextField text;
	String info = "";
	final Context context;
	final MutableMacro macro;

	public CircuitEditor(BlockDef def) {
		this.blockVAO = genBlockVAO(64);
		this.traceVAO = genTraceVAO(64);
		this.blocks = new IndexedSet<>(new Block[16]);
		this.traces = new IndexedSet<>(new Trace[16]);
		this.traceLookup = new HashMap<>();
		this.context = new Context();
		this.icons = def.module.cache.icons;
		this.macro = new MutableMacro(def);
		
		fullRedraw();
		glUseProgram(traceP);
		glUniform2f(trace_lineSize, 0.25F, 1F);
		checkGLErrors();
		
		try {
			CircuitFile.readLayout(CircuitFile.readBlock(def), null, this);
		} catch(IOException e) {}
		if (blocks.isEmpty()) {
			BlockDef io = def.module.findIO("out");
			if (io != null) {
				int i = 0;
				for (String s : def.outs) {
					Block block = new Block(io, 1, this);
					block.setText(s);
					block.pos(2, i * 4).place();
				}
			}
			io = def.module.findIO("in");
			if (io != null) {
				int i = 0;
				for (String s : def.ins) {
					Block block = new Block(io, 1, this);
					block.setText(s);
					block.pos(-2 - block.w, i * 4).place();
				}
			}
		}
	}

	@Override
	public void onResize(int w, int h) {
	}

	@Override
	public void redraw() {
		updateTypeCheck();
		if (redraw < 0) redrawTraces();
		redraw = 0;
		//draw frame
		float scaleX = (float)(zoom * 2) / (float)WIDTH;
		float scaleY = (float)(zoom * 2) / (float)HEIGHT;
		float ofsX = (float)this.ofsX * scaleX * 0.5F;
		float ofsY = (float)this.ofsY * scaleY * 0.5F;
		float[] mat = new float[] {
			scaleX,  0, 0,
			0, -scaleY, 0,
			ofsX,-ofsY, 0,
		};
		glUseProgram(traceP);
		glUniformMatrix3fv(trace_transform, false, mat);
		traceVAO.draw();
		checkGLErrors();
		
		icons.bind();
		glUniformMatrix3fv(block_transform, false, mat);
		blockVAO.count = blocks.size() * 4;
		blockVAO.draw();
		checkGLErrors();
		
		MacroState ms = macro.state;
		if (ms != null) {
			for (SignalError e = ms.errors; e != null; e = e.next) {
				Block block = macro.blocks[e.nodeId];
				if (block != null)
					addSel(block.x * 2, block.y * 2, block.w * 2, block.h * 2, FG_RED);
			}
			for (NodeState ns = ms.first; ns != null; ns = ns.next) {
				Block block = macro.blocks[ns.node.idx];
				if (block != null)
					addSel(block.x * 2, block.y * 2, block.w * 2, block.h * 2, FG_GREEN);
			}
		}
		if (mode == M_MULTI_SEL || mode == M_MULTI_MOVE)
			addSel(min(lmx, mx), min(lmy, my), abs(mx - lmx), abs(my - lmy), FG_GREEN_L);
		if (selBlock != null)
			addSel(selBlock.x * 2, selBlock.y * 2, selBlock.w * 2, selBlock.h * 2, FG_BLUE_L);
		if (selTr != null)
			addSel(selTr.x() * 2 - 1, selTr.y() * 2 - 1, 2, 2, FG_RED_L);
		drawSel(ofsX, -ofsY, 0.5F * scaleX, -0.5F * scaleY, 0F, 1F);
		
		if (text != null && selBlock != null)
			text.redraw(selBlock.textX(), selBlock.textY(), 2, 3, 0);
		for (Block block : blocks) block.printText();
		drawText(ofsX, -ofsY, scaleX * 0.25F, scaleY * -0.25F);
		print(info, FG_YELLOW_L, 0, -1, 1, 1);
		drawText(-1F, -1F, 32F / (float)WIDTH, -48F / (float)HEIGHT);
	}

	private void redrawTraces() {
		MacroState ms = macro.state;
		traceVAO.clear();
		for (Trace t : traceLookup.values())
			t.draw(traceVAO, ms, true);
		checkGLErrors();
	}

	@Override
	public void onScroll(double dx, double dy) {
		if (dy == 0) return;
		int zoom1 = max(4, zoom + (dy > 0 ? 4 : -4));
		if (zoom1 == zoom) return;
		float scale = (float)zoom / (float)zoom1;
		float dox = ofsX + mx, doy = ofsY + my;
		ofsX = round(dox * scale) - mx;
		ofsY = round(doy * scale) - my;
		zoom = zoom1;
		refresh(0);
	}

	@Override
	public boolean onMouseMove(double x, double y) {
		int gx = (int)floor(x * (double)WIDTH / (double)zoom) - ofsX;
		int gy = (int)floor(y * (double)HEIGHT / (double)zoom) - ofsY;
		if (gx != mx || gy != my)
			moveSel(gx, gy);
		return true;
	}

	private void moveSel(int x, int y) {
		if (panning) {
			ofsX += x - mx;
			ofsY += y - my;
			refresh(0);
			return;
		}
		mx = x; my = y;
		switch(mode) {
		case M_IDLE:
			selTrace(traceLookup.get(Trace.key(x + 1 >> 1, y + 1 >> 1)));
			Block old = selBlock;
			selBlock = null;
			for (Block block : blocks)
				if (block.isInside(x >> 1, y >> 1)) {
					selBlock = block;
					//if (block.node != null && block.node.error != null)
					//	info = block.node.error.getLocalizedMessage();
					break;
				}
			if (selBlock != old) refresh(0);
			return;
		case M_TRACE_SEL:
			if (selTr.pin < 0) {
				selTr.pickup();
				mode = M_TRACE_MV;
			} else {
				selBlock = selTr.block;
				mode = M_BLOCK_SEL;
				lmx = x;
				lmy = y;
				return;
			}
		case M_TRACE_MV, M_TRACE_DRAW:
			selTr.pos(x + 1 >> 1, y + 1 >> 1);
			return;
		case M_MULTI_SEL:
			refresh(0);
		default: return;
		case M_BLOCK_SEL:
			if (moving.isEmpty())
				moving.add(selBlock.pickup());
		case M_MULTI_MOVE:
		}
		int dx = x - lmx >> 1, dy = y - lmy >> 1;
		if (dx == 0 && dy == 0) return;
		for (IMovable m : moving)
			m.pos(m.x() + dx, m.y() + dy);
		lmx += dx << 1;
		lmy += dy << 1;
	}

	@Override
	public void onMouseButton(int button, int action, int mods) {
		switch(action | button << 1) {
		case GLFW_PRESS | GLFW_MOUSE_BUTTON_RIGHT<<1:
			panning = true;
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_RIGHT<<1:
			panning = false;
			break;
		case GLFW_PRESS | GLFW_MOUSE_BUTTON_LEFT<<1:
			text = null;
			if (selTr == null) {
				if (mode != M_IDLE) break;
				lmx = mx;
				lmy = my;
				mode = selBlock == null ? M_MULTI_SEL : M_BLOCK_SEL;
				refresh(0);
			} else if (mode == M_TRACE_DRAW) {
				Trace t = selTr.place();
				if (selTrace(t)) mode = M_IDLE;
				else mode = M_TRACE_SEL;
			} else if (mode == M_IDLE)
				mode = M_TRACE_SEL;
			else if (mode == M_BLOCK_SEL) {
				selBlock.place();
				moving.clear();
				mode = M_IDLE;
			}
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_LEFT<<1:
			if (mode == M_MULTI_SEL) {
				int x0 = min(lmx, mx) >> 1, x1 = max(lmx, mx) + 1 >> 1,
				    y0 = min(lmy, my) >> 1, y1 = max(lmy, my) + 1 >> 1;
				for (Trace trace : traceLookup.values())
					if (trace.pin < 0 && trace.inRange(x0, y0, x1, y1))
						moving.add(trace);
				for (Block block : blocks)
					if (block.inRange(x0, y0, x1, y1))
						moving.add(block);
				for (IMovable m : moving) m.pickup();
				for (IMovable m : moving) m.pickup();
				lmx = mx;
				lmy = my;
				mode = moving.isEmpty() ? M_IDLE : M_MULTI_MOVE;
				refresh(0);
			} else if (mode == M_MULTI_MOVE) {
				for (IMovable m : moving)
					m.place();
				moving.clear();
				mode = M_IDLE;
				fullRedraw();
			} else if (mode == M_BLOCK_SEL) {
				Block block = selBlock;
				block.place();
				if (block.args.length > 0) {
					text = new TextField(t -> {
						block.setText(t);
						block.redraw();
						//TODO text edit
					});
					text.set(block.text(), 1 + (lmx * 2 - block.textX() >> 2));
				}
				moving.clear();
				mode = M_IDLE;
				refresh(0);
			} else if (mode == M_TRACE_MV) {
				selTr.place();
				mode = M_IDLE;
			} else if (mode == M_TRACE_SEL)
				if (!selTr.isOut()) {
					mode = M_TRACE_DRAW;
					Trace t = new Trace(this).pos(mx + 1 >> 1, my + 1 >> 1);
					selTr.connect(t);
					selTrace(t);
				} else mode = M_IDLE;
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_MIDDLE<<1:
			int x = mx >> 1, y = my >> 1;
			for (Block block : blocks)
				if (block.isInside(x, y)) {
					addBlock(block.def);
					break;
				}
			break;
		}
		lock(panning || mode != M_IDLE && mode != M_TRACE_DRAW);
	}

	@Override
	public void onKeyInput(int key, int scancode, int action, int mods) {
		if (action == GLFW_RELEASE) return;
		if (text != null && text.onKeyInput(key, mods)) return;
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
		switch(key) {
		case GLFW_KEY_DELETE:
			if (selBlock != null)
				selBlock.remove();
			if (selTr != null && selTr.pin < 0)
				selTr.remove();
			for (IMovable m : moving) m.remove();
			selBlock = null;
			selTr = null;
			moving.clear();
			mode = M_IDLE;
			break;
		case GLFW_KEY_S:
			if (!ctrl) break;
			try {
				CircuitFile.writeLayout(macro.def, blocks, traces);
				info = "saved!";
			} catch(IOException e) {
				e.printStackTrace();
				info = e.toString();
			}
			break;
		case GLFW_KEY_W:
			if (ctrl) glfwSetWindowShouldClose(WINDOW, true);
			break;
		case GLFW_KEY_D:
			if (ctrl) cleanUpTraces();
			break;
		case GLFW_KEY_B:
			if (!ctrl) break;
			//new MacroEdit(icons, new BlockDef(""));
			break;
		case GLFW_KEY_HOME:
			ofsX = ofsY = 0;
			refresh(0);
			break;
		}
	}

	boolean selTrace(Trace t) {
		if (t == selTr) return false;
		selTr = t;
		StringBuilder sb = new StringBuilder();
		if (t != null && t.block != null && t.pin >= 0) {
			BlockDef def = t.block.def;
			String[] names = t.isOut() ? def.outs : def.ins;
			int i = t.isOut() ? t.pin : t.pin - t.block.outs;
			sb.append(names[Math.min(i, names.length - 1)]).append(": ");
		}
		while(t != null && !t.isOut()) t = t.from;
		if (t != null && t.block != null) {
			Value sg = t.value(macro.state);
			if (sg != null) sb.append(sg.toString());
		}
		info = sb.toString();
		refresh(0);
		return true;
	}

	@Override
	public void onCharInput(int cp) {
		if (text != null) text.onCharInput(cp);
	}

	public void addBlock(BlockDef type) {
		text = null;
		lmx = mx; lmy = my;
		selBlock = new Block(type, 1, this)
		.pos(mx + 1 - selBlock.w >> 1, my + 1 - selBlock.h >> 1);
		Trace t = selBlock.io[0];
		if (selTr != null && t.isOut())
			(selTr.pin >= 0 ? selTr : selTr.to).connect(t);
		selTrace(t);
		mode = M_BLOCK_SEL;
	}

	/**Modify coordinates of a trace in its vertex buffer.
	 * @param tr */
	public void updatePos(Trace tr) {
		if (redraw < 0) return;
		Main.refresh(0);
		redraw |= 1;
		tr.draw(traceVAO, macro.state, false);
	}

	public void redrawSignal(int idx) {
		fullRedraw();
	}

	/**Schedule a complete re-render of all vertex buffers. */
	public void fullRedraw() {
		refresh(0);
		redraw = -1;
	}

	@Override
	public void close() {
		glDeleteBuffers(blockVAO.buffer);
		glDeleteBuffers(traceVAO.buffer);
	}

	private void clear() {
		blocks.clear();
		traces.clear();
		traceLookup.clear();
		context.clear();
	}

	private void cleanUpTraces() {
		ArrayList<Trace> toRemove = new ArrayList<>();
		for (Trace tr : traceLookup.values())
			if (tr.pin < 0 && (tr.to == null || tr.from == null))
				toRemove.add(tr);
		int n = 0;
		while(!toRemove.isEmpty()) {
			n++;
			Trace tr = toRemove.remove(toRemove.size() - 1);
			for (Trace t = tr.to; t != null; t = t.adj)
				if (t.pin < 0)
					toRemove.add(t);
			Trace f = tr.from;
			tr.remove();
			if (f != null && f.pin < 0 && f.to == null)
				toRemove.add(f);
		}
		info = "removed " + n + " traces!";
		refresh(0);
	}

	private void updateTypeCheck() {
		while(!traceUpdates.isEmpty())
			traceUpdates.remove().update();
		if (context.tick(1000)) {
			//TODO repeated frame updates
		}
	}

}
