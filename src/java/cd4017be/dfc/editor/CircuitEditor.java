package cd4017be.dfc.editor;

import java.io.*;
import java.util.*;

import cd4017be.compiler.*;
import cd4017be.compiler.instr.ConstList;
import cd4017be.compiler.instr.Function;
import cd4017be.util.*;

import static cd4017be.compiler.LoadingCache.*;
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
	M_MULTI_SEL = 5, M_MULTI_MOVE = 6, M_BLOCK_SCALE = 7;

	public final IndexedSet<Block> blocks;
	public final IndexedSet<Trace> traces;
	final ArrayList<CircuitObject> moving = new ArrayList<>();
	final Palette palette;
	/** GL vertex arrays */
	final VertexArray blockVAO, traceVAO;
	final TextField text = new TextField(this::setText);
	final ArrayList<String> autoComplete = new ArrayList<>();
	/** mouse grid offset and zoom */
	int ofsX, ofsY, zoom = 16, dSize;
	boolean panning = false;
	/** current editing mode */
	byte mode;
	/** mouse positions */
	int mx = -1, my = -1, lmx, lmy;
	Trace selTr;
	Block selBlock, editing, errorBlock;
	SignalError lastError;
	int editArg;
	boolean textModified, reRunTypecheck;
	String info = "";
	NodeContext context;

	public CircuitEditor() {
		this.blockVAO = genBlockVAO(64);
		this.traceVAO = genTraceVAO(64);
		this.blocks = new IndexedSet<>(new Block[64]);
		this.traces = new IndexedSet<>(new Trace[64]);
		this.palette = new Palette(this);
		Main.GUI.add(this);
		Main.GUI.add(palette);
		
		glUseProgram(traceP);
		glUniform2f(trace_lineSize, 1F, 1F);
		checkGLErrors();
	}

	public void open(BlockDef def) {
		clear();
		palette.setModule(def.module);
		context = new NodeContext(def, true);
		reRunTypecheck = true;
		try {
			CircuitFile.readLayout(CircuitFile.readBlock(def), def.module, this);
		} catch(IOException e) {
			e.printStackTrace();
		}
		if (blocks.isEmpty() && def.type.equals("block")) {
			int i = 0;
			for (String s : def.outs) {
				Block block = new Block(OUT_BLOCK, 1);
				block.args[0] = s;
				block.updateSize();
				block.pos(2, i * 4, this).add(this);
				i++;
			}
			i = 0;
			for (String s : def.ins) {
				Block block = new Block(IN_BLOCK, 1);
				block.args[0] = s;
				block.updateSize();
				block.pos(-2 - block.w, i * 4, this).add(this);
				i++;
			}
			for (String s : def.args) {
				Block block = new Block(IN_BLOCK, 1);
				block.args[0] = s;
				block.updateSize();
				block.pos(-2 - block.w, i * 4, this).add(this);
				i++;
			}
		}
	}

	@Override
	public void onResize(int w, int h) {
	}

	@Override
	public void redraw() {
		if (reRunTypecheck)
			runTypeCheck();
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
		TRACES.bind();
		glUniformMatrix3fv(trace_transform, false, mat);
		traceVAO.count = traces.size() * 4;
		traceVAO.draw();
		checkGLErrors();
		
		ATLAS.bind();
		glUniformMatrix3fv(block_transform, false, mat);
		blockVAO.count = blocks.size() * 4;
		blockVAO.draw();
		checkGLErrors();
		
		if (lastError != null && errorBlock != null) {
			Block block = errorBlock;
			addSel(block.x * 2, block.y * 2, block.w * 2, block.h * 2, FG_RED);
			if (block == selBlock) {
				String msg = lastError.getMessage();
				print(msg, BG_BLACK_T | FG_RED, block.x * 2 + block.w - msg.length(), block.y * 2 - 4, 2, 3);
			}
		}
		if (mode == M_MULTI_SEL || mode == M_MULTI_MOVE)
			addSel(min(lmx, mx), min(lmy, my), abs(mx - lmx), abs(my - lmy), FG_GREEN_L);
		if (selBlock != null) {
			int h = selBlock.h * 2;
			if (mode == M_BLOCK_SCALE) {
				h += dSize * selBlock.def.model.rh * 2;
				String s = "size " + (dSize + selBlock.size());
				print(s, FG_BLUE_L, selBlock.x * 2 + selBlock.w - s.length(), selBlock.y * 2 + h, 2, 3);
			}
			addSel(selBlock.x * 2, selBlock.y * 2, selBlock.w * 2, h, FG_BLUE_L);
		}
		if (selTr != null)
			addSel(selTr.x() * 2 - 1, selTr.y() * 2 - 1, 2, 2, FG_RED_L);
		int ac0 = 0, ac1 = 0, x = 0, y = 0;
		if (editing != null && editArg >= 0) {
			String s = text.get();
			int l = s.length();
			x = editing.textX() - l;
			y = editing.textY() + editArg * 4;
			text.redraw(x, y, 2, 3, 0);
			addSel(x, y, l * 2, 4, FG_YELLOW_L);
			s = s.substring(0, Math.min(text.cursor(), s.length()));
			ac0 = Collections.binarySearch(autoComplete, s);
			ac1 = Collections.binarySearch(autoComplete, s + '\uffff');
			if (ac0 < 0) ac0 ^= -1;
			if (ac1 < 0) ac1 ^= -1;
		}
		drawSel(ofsX, -ofsY, 0.5F * scaleX, -0.5F * scaleY, 0F, 1F);
		for (Block block : blocks) block.printText();
		drawText(ofsX, scaleY * -.25F - ofsY, scaleX * 0.5F, scaleY * -0.5F);
		if (ac1 > ac0) {
			y += 4;
			int l = 0;
			for (int i = ac0, j = 0; i < ac1; i++, j++) {
				String s = autoComplete.get(i);
				print(s, FG_GRAY_L, x, y + j * 3, 2, 3);
				l = Math.max(l, s.length());
			}
			addSel(x, y, l * 2, 1 + 3 * (ac1 - ac0), BG_BLACK_T | FG_GRAY_D);
			drawSel(ofsX, -ofsY, 0.5F * scaleX, -0.5F * scaleY, 0F, 1F);
			drawText(ofsX, scaleY * -.25F - ofsY, scaleX * 0.5F, scaleY * -0.5F);
		}
		print(info, FG_YELLOW_L, 0, -1, 1, 1);
		drawText(-1F, -1F, 16F / (float)WIDTH, -24F / (float)HEIGHT);
	}

	private void runTypeCheck() {
		try {
			lastError = null;
			errorBlock = null;
			Arguments args = context.typeCheck(blocks);
			for (Block block : blocks)
				block.updateColors(args);
		} catch(SignalError e) {
			int i = e.pos;
			lastError = e;
			errorBlock = i >= 0 && i < blocks.size() ? blocks.get(i) : null;
			info = e.getMessage();
		} catch (Throwable e) {
			e.printStackTrace();
			info = e.getMessage();
		}
		reRunTypecheck = false;
	}

	private void setText(String text) {
		editing.args[editArg] = text;
		editing.updateSize();
		textModified = true;
	}

	private void updateArg() {
		if (editing == null || !textModified) return;
		reRunTypecheck = true;
		textModified = false;
	}

	private void editText(Block block, int row, int col) {
		if (block == null || row < 0 || row >= block.args.length) {
			if (editing != null) refresh(0);
			updateArg();
			editing = null;
			editArg = -1;
			text.set("", 0);
			autoComplete.clear();
		} else {
			if (block != editing || row != editArg) {
				updateArg();
				editing = block;
				editArg = row;
				autoComplete.clear();
				block.def.assembler.getAutoCompletions(block, row, autoComplete, context);
				Collections.sort(autoComplete);
				refresh(0);
			}
			text.set(block.args[row], col);
		}
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
		case M_IDLE: {
			int gx = x + 1 >> 1, gy = y + 1 >> 1;
			Trace sel = null;
			Block old = selBlock;
			selBlock = null;
			for (Block block : blocks) {
				for (int i = 0; sel == null && i < block.outs(); i++) {
					Trace t = block.io[i];
					if (t.x() == gx && t.y() == gy) sel = t;
				}
				if (block.isInside(x >> 1, y >> 1)) {
					selBlock = block;
					break;
				}
			}
			if (sel == null)
				for (Trace t : traces)
					if (t.x() == gx && t.y() == gy) {
						sel = t;
						break;
					}
			selTrace(sel);
			if (selBlock != old) refresh(0);
			if (selTr != null) glfwSetCursor(WINDOW, MAIN_CURSOR);
			else if (selBlock == null) glfwSetCursor(WINDOW, SEL_CURSOR);
			else if (selBlock.def.varSize() && (selBlock.y + selBlock.h) * 2 - my < 2)
				glfwSetCursor(WINDOW, VRESIZE_CURSOR);
			else {
				int dy = my - selBlock.textY() >> 2;
				if (dy >= 0 && dy < selBlock.args.length) {
					glfwSetCursor(WINDOW, TEXT_CURSOR);
				} else glfwSetCursor(WINDOW, MAIN_CURSOR);
			}
			return;
		}
		case M_TRACE_SEL:
			glfwSetCursor(WINDOW, MOVE_CURSOR);
			if (selTr.pin < 0) {
				selTr.pickup(this);
				mode = M_TRACE_MV;
			} else {
				selBlock = selTr.block;
				mode = M_BLOCK_SEL;
				lmx = x;
				lmy = y;
				return;
			}
		case M_TRACE_MV, M_TRACE_DRAW:
			glfwSetCursor(WINDOW, MOVE_CURSOR);
			selTr.pos(x + 1 >> 1, y + 1 >> 1, this);
			return;
		case M_MULTI_SEL:
			glfwSetCursor(WINDOW, SEL_CURSOR);
			refresh(0);
			return;
		case M_BLOCK_SCALE:
			glfwSetCursor(WINDOW, VRESIZE_CURSOR);
			if (dSize != (dSize = floorDiv(my - lmy + 2, selBlock.def.model.rh * 2)))
				refresh(0);
			return;
		default: return;
		case M_BLOCK_SEL:
			if (moving.isEmpty())
				moving.add(selBlock.pickup(this));
		case M_MULTI_MOVE:
			glfwSetCursor(WINDOW, MOVE_CURSOR);
		}
		int dx = x - lmx >> 1, dy = y - lmy >> 1;
		if (dx == 0 && dy == 0) return;
		for (CircuitObject m : moving)
			m.pos(m.x() + dx, m.y() + dy, this);
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
			if (selTr == null) {
				if (mode != M_IDLE) break;
				lmx = mx;
				lmy = my;
				if (selBlock == null) mode = M_MULTI_SEL;
				else if (selBlock.def.varSize() && (selBlock.y + selBlock.h) * 2 - my < 2) {
					dSize = 0;
					mode = M_BLOCK_SCALE;
				} else mode = M_BLOCK_SEL;
				refresh(0);
			} else if (mode == M_TRACE_DRAW) {
				Trace t = selTr.place(this);
				if (selTrace(t)) mode = M_IDLE;
				else mode = M_TRACE_SEL;
			} else if (mode == M_IDLE)
				mode = M_TRACE_SEL;
			else if (mode == M_BLOCK_SEL) {
				selBlock.place(this);
				moving.clear();
				mode = M_IDLE;
			}
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_LEFT<<1:
			if (mode != M_BLOCK_SEL)
				editText(null, -1, 0);
			if (mode == M_MULTI_SEL) {
				int x0 = min(lmx, mx) >> 1, x1 = max(lmx, mx) + 1 >> 1,
				    y0 = min(lmy, my) >> 1, y1 = max(lmy, my) + 1 >> 1;
				for (Trace trace : traces)
					if (trace.pin < 0 && trace.inRange(x0, y0, x1, y1))
						moving.add(trace);
				for (Block block : blocks)
					if (block.inRange(x0, y0, x1, y1))
						moving.add(block);
				for (CircuitObject m : moving) m.pickup(this);
				for (CircuitObject m : moving) m.pickup(this);
				lmx = mx;
				lmy = my;
				mode = moving.isEmpty() ? M_IDLE : M_MULTI_MOVE;
				refresh(0);
			} else if (mode == M_MULTI_MOVE) {
				for (CircuitObject m : moving)
					m.place(this);
				moving.clear();
				mode = M_IDLE;
			} else if (mode == M_BLOCK_SEL) {
				Block block = selBlock;
				block.place(this);
				moving.clear();
				mode = M_IDLE;
				int row = lmy - block.textY() >> 2;
				int col = row >= 0 && row < block.args.length ?
					(lmx - block.textX() + block.args[row].length() + 1 >> 1) : 0;
				editText(block, row, col);
				refresh(0);
			} else if (mode == M_BLOCK_SCALE) {
				selBlock = selBlock.resize(dSize, this);
				mode = M_IDLE;
				refresh(0);
			} else if (mode == M_TRACE_MV) {
				selTr.place(this);
				mode = M_IDLE;
			} else if (mode == M_TRACE_SEL)
				if (!selTr.isOut()) {
					mode = M_TRACE_DRAW;
					Trace t = new Trace().pos(mx + 1 >> 1, my + 1 >> 1, this);
					t.add(this);
					selTr.connect(t, this);
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
		if (editing != null && text.onKeyInput(key, mods)) return;
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
		switch(key) {
		case GLFW_KEY_PAGE_UP:
			if (editing != null)
				editText(editing, editArg - 1, text.cursor());
			break;
		case GLFW_KEY_PAGE_DOWN:
			if (editing != null)
				editText(editing, editArg + 1, text.cursor());
			break;
		case GLFW_KEY_ENTER:
			if (editing != null)
				editText(null, -1, 0);
			break;
		case GLFW_KEY_DELETE:
			if (selBlock != null)
				selBlock.remove(this);
			if (selTr != null && selTr.pin < 0)
				selTr.remove(this);
			for (CircuitObject m : moving) m.remove(this);
			selBlock = null;
			selTr = null;
			moving.clear();
			mode = M_IDLE;
			break;
		case GLFW_KEY_S:
			if (!ctrl) break;
			try {
				BlockDef def = context.def;
				CircuitFile.writeLayout(def, blocks, traces);
				info = "saved!";
				if (def.assembler instanceof Function m) m.reset();
				else if (def.assembler instanceof ConstList cl) {
					try {
						cl.compile();
						info += " compiled!";
					} catch (SignalError e) {
						info += " " + e.getMessage();
					}
				}
			} catch(IOException e) {
				e.printStackTrace();
				info = e.toString();
			}
			refresh(0);
			break;
		case GLFW_KEY_W:
			if (ctrl) glfwSetWindowShouldClose(WINDOW, true);
			break;
		case GLFW_KEY_D:
			if (ctrl) cleanUpTraces();
			break;
//		case GLFW_KEY_T:
//			if (!ctrl) break;
//			info = "type check restarted";
//			refresh(0);
//			break;
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
			int i = t.isOut() ? t.pin : t.pin - t.block.outs();
			sb.append(names[Math.min(i, names.length - 1)]).append(": ");
		}
		if (t != null) {
			Value v = t.value(context.state);
			if (v != null) sb.append(v.toString());
			else sb.append("[not evaluated]");
		}
		info = sb.toString();
		refresh(0);
		return true;
	}

	@Override
	public void onCharInput(int cp) {
		if (editing != null) text.onCharInput(cp);
	}

	public void addBlock(BlockDef type) {
		editText(null, -1, 0);
		lmx = mx; lmy = my;
		selBlock = new Block(type, 1);
		selBlock.pos(mx + 1 - selBlock.w >> 1, my + 1 - selBlock.h >> 1, this);
		selBlock.add(this);
		Trace t = selBlock.io[0];
		if (selTr != null && t.isOut())
			(selTr.pin >= 0 ? selTr : selTr.to).connect(t, this);
		selTrace(t);
		mode = M_BLOCK_SEL;
	}

	@Override
	public void close() {
		glDeleteBuffers(blockVAO.buffer);
		glDeleteBuffers(traceVAO.buffer);
	}

	private void clear() {
		editText(null, -1, 0);
		moving.clear();
		blocks.clear();
		traces.clear();
		mode = M_IDLE;
		selBlock = errorBlock = null;
		selTr = null;
		lastError = null;
		refresh(0);
	}

	private void cleanUpTraces() {
		ArrayList<Trace> toRemove = new ArrayList<>();
		for (Trace tr : traces)
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
			tr.remove(this);
			if (f != null && f.pin < 0 && f.to == null)
				toRemove.add(f);
		}
		info = "removed " + n + " traces!";
		refresh(0);
	}

}
