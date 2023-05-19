package cd4017be.dfc.editor.circuit;

import java.io.*;
import java.util.*;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.editor.circuit.BlockList.BlockConsumer;
import cd4017be.dfc.editor.gui.*;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.CircuitFile.Layout;
import cd4017be.dfc.lang.Interpreter.Task;
import cd4017be.dfc.lang.Node.Vertex;
import cd4017be.dfc.lang.builders.BasicConstructs;
import cd4017be.dfc.lang.builders.ConstList;
import cd4017be.dfc.lang.builders.Function;
import cd4017be.dfc.lang.builders.Macro;
import cd4017be.dfc.lang.instructions.*;
import cd4017be.util.*;
import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static cd4017be.dfc.lang.LoadingCache.*;
import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL20C.*;

/**Implements the circuit editor user interface.
 * @author CD4017BE */
public class CircuitEditor extends GuiGroup implements BlockConsumer {

	/**Editing modes */
	private static final byte M_IDLE = 0, M_BLOCK_SEL = 1, M_TRACE_SEL = 2, M_TRACE_MV = 3, M_TRACE_DRAW = 4,
	M_MULTI_SEL = 5, M_MULTI_MOVE = 6, M_BLOCK_SCALE = 7;

	public final IndexedSet<Block> blocks;
	public final IndexedSet<Trace> traces;
	final ArrayList<CircuitObject> moving = new ArrayList<>();
	final Palette palette;
	final ControlPanel panel;
	/** GL vertex arrays */
	final VertexArray blockVAO, traceVAO;
	final TextField text;
	final ArrayList<String> autoComplete = new ArrayList<>();
	final Interpreter ip;
	/** mouse grid offset and zoom */
	int ofsX, ofsY, dSize;
	boolean panning = false;
	/** current editing mode */
	byte mode;
	/** mouse positions */
	int lmx, lmy;
	Trace selTr;
	Block selBlock, editing, errorBlock;
	SignalError lastError;
	int editArg;
	boolean textModified, reRunTypecheck;
	NodeContext context;
	Task result;

	public CircuitEditor(GuiGroup parent) {
		super(parent, 4);
		parent.add(this);
		this.palette = new Palette(parent, this);
		this.panel = new ControlPanel(parent);
		this.blockVAO = genBlockVAO(64);
		this.traceVAO = genTraceVAO(64);
		this.blocks = new IndexedSet<>(new Block[64]);
		this.traces = new IndexedSet<>(new Trace[64]);
		this.ip = new Interpreter();
		this.text = new TextField(this).color(FG_YELLOW_L).action(this::setText).model(null);
		
		glUseProgram(traceP);
		glUniform2f(trace_lineSize, 1F, 1F);
		checkGLErrors();
	}

	public void open(BlockDef def) {
		clear();
		palette.setModule(def.module);
		ip.cancel();
		result = ip.new Task(null, task -> {});
		context = new NodeContext(def, true);
		reRunTypecheck = true;
		synchronized(def) {
			try {
				Layout layout = CircuitFile.readLayout(CircuitFile.readBlock(def), def.module);
				for (Trace trace : layout.traces())
					if (trace != null) trace.add(this);
				for (Block block : layout.blocks())
					block.add(this);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		if (blocks.isEmpty() && !def.type.equals("const")) {
			int i = 0;
			for (String s : def.outs) {
				Block block = new Block(OUT_BLOCK, 1);
				block.args[0] = s;
				block.updateSize();
				block.pos(2, i * 4).add(this);
				i++;
			}
			i = 0;
			for (String s : def.ins) {
				Block block = new Block(IN_BLOCK, 1);
				block.args[0] = s;
				block.updateSize();
				block.pos(-2 - block.w, i * 4).add(this);
				i++;
			}
			for (String s : def.args) {
				Block block = new Block(IN_BLOCK, 1);
				block.args[0] = s;
				block.updateSize();
				block.pos(-2 - block.w, i * 4).add(this);
				i++;
			}
		}
	}

	@Override
	public void onResize(long window, int w, int h) {
		x0 = Palette.WIDTH * 2;
		x1 = w;
		y0 = 0;
		y1 = h - ControlPanel.HEIGHT * 2;
	}

	@Override
	public void redraw() {
		if (reRunTypecheck) {
			context.typeCheck(ip, blocks, task -> Main.runAsync(() -> applyResult(task)));
			reRunTypecheck = false;
		}
		//draw frame
		if (redraw <= 0) return;
		redraw--;
		int w = x1 - x0, h = y1 - y0, y = parent.y1 - y1;
		glViewport(x0, y, w, h);
		glScissor(x0, y, w, h);
		float sx = 2F / w * scale, sy = -2F / h * scale;
		float ox = (float)ofsX * sx;
		float oy = (float)ofsY * sy;
		{
			float s = scale <= 1 ? 0.0625F : 0.25F;
			float isx = s / sx, rox = ofsX * -s;
			float isy = s / sy, roy = ofsY * s;
			drawGrid(rox - isx, roy - isy, rox + isx, roy + isy, s / scale);
		}
		
		TRACES.bind();
		transform(trace_transform, ox, oy, sx * 4F, sy * 4F);
		traceVAO.count = traces.size() * 4;
		traceVAO.draw();
		checkGLErrors();
		
		ICONS.bind();
		transform(block_transform, ox, oy, sx * 4F, sy * 4F);
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
			addSel(min(lmx, mx) >> 1, min(lmy, my) >> 1, abs(mx - lmx) + 1 >> 1, abs(my - lmy) + 1 >> 1, FG_GREEN_L);
		if (selBlock != null) {
			int bh = selBlock.h * 2;
			if (mode == M_BLOCK_SCALE) {
				bh += dSize * selBlock.def.model.rh() * 2;
				String s = "size " + (dSize + selBlock.size());
				print(s, FG_BLUE_L, selBlock.x * 2 + selBlock.w - s.length(), selBlock.y * 2 + bh, 2, 3);
			}
			addSel(selBlock.x * 2, selBlock.y * 2, selBlock.w * 2, bh, FG_BLUE_L);
		}
		if (selTr != null)
			addSel(selTr.x() * 2 - 1, selTr.y() * 2 - 1, 2, 2, FG_RED_L);
		for (Block block : blocks) block.printText();
		for (Drawable d : drawables) d.redraw();
		drawSel( ox, oy,      sx * 2F, sy * 2F, 0F, 1F, false);
		drawText(ox, oy + sy, sx * 2F, sy * 2F,         false);
		drawSel( ox, oy,      sx * 2F, sy * 2F, 0F, 1F, true);
		drawText(ox, oy + sy, sx * 2F, sy * 2F,         true);
	}

	private void applyResult(Task task) {
		result = task;
		task.log();
		lastError = task.error;
		if (lastError == null) {
			errorBlock = null;
		} else {
			int i = lastError.pos;
			errorBlock = i >= 0 && i < blocks.size() ? blocks.get(i) : null;
			panel.statusMsg(lastError.getMessage());
		}
		if (task.vars != null)
			for (Block block : blocks)
				block.updateColors(task.vars);
	}

	private void setText(TextField tf, boolean finish) {
		if (finish) {
			updateArg();
			editing = null;
			editArg = -1;
			text.text("");
			autoComplete.clear();
			return;
		}
		Block editing = this.editing;
		editing.args[editArg] = tf.text;
		int dw = -editing.w;
		editing.updateSize();
		if ((dw += editing.w) > 0) {
			int l = 32768, r = 32768;
			for (Block block : blocks) {
				if (block.y + block.h <= editing.y || block.y >= editing.y + editing.h || block == editing) continue;
				if (block.x < editing.x)
					l = min(l, editing.x - (block.x + block.w));
				else r = min(r, block.x - (editing.x + editing.w));
			}
			dw = min(-r, l);
		}
		if (dw > 0) editing.pos(editing.x - dw, editing.y);
		else editing.updatePins();
		textModified = true;
		updateTextPos();
	}

	private void updateTextPos() {
		String s = text.text;
		int l = s.length();
		s = s.substring(0, min(text.cursor(), l));
		int ac0 = Collections.binarySearch(autoComplete, s);
		int ac1 = Collections.binarySearch(autoComplete, s + '\uffff');
		if (ac0 < 0) ac0 ^= -1;
		if (ac1 < 0) ac1 ^= -1;
		text.autocomplete.clear();
		if (ac1 > ac0)
			text.autocomplete.addAll(autoComplete.subList(ac0, ac1));
		text.pos(editing.textX() - l << 1, editing.textY() * 2 + editArg * 8, l * 4, 8);
	}

	private void updateArg() {
		if (editing == null || !textModified) return;
		if (editing == errorBlock || editing.outs() == 0)
			reRunTypecheck = true;
		else for (Node n : editing.outs)
			if (n != null && n.addr(0) > 0) {
				reRunTypecheck = true;
				break;
			}
		textModified = false;
	}

	private void editText(Block block, int row, int col) {
		if (block == null || row < 0 || row >= block.args.length) {
			updateArg();
			editing = null;
			editArg = -1;
			text.text("");
			autoComplete.clear();
			focus(null);
		} else {
			if (block != editing || row != editArg) {
				updateArg();
				editing = block;
				editArg = row;
				autoComplete.clear();
				if (context.def.assembler instanceof Macro)
					for (String s : context.def.args)
						autoComplete.add(s);
				block.def.parser(row).getAutoCompletions(block, row, autoComplete, context);
				Collections.sort(autoComplete);
			}
			text.text(block.args[row]).cursor(col);
			updateTextPos();
			focus(text);
		}
	}

	@Override
	public void updateHover() {
		if (parent.hovered() != this)
			glfwSetCursor(Main.WINDOW, Main.MAIN_CURSOR);
	}

	@Override
	public boolean onScroll(double dx, double dy) {
		if (dy == 0) return false;
		int zoom = max(1, scale + (dy > 0 ? 1 : -1));
		if (zoom == scale) return true;
		float s = (float)scale / (float)zoom;
		float dox = ofsX + mx, doy = ofsY + my;
		ofsX = round(dox * s) - mx;
		ofsY = round(doy * s) - my;
		scale = zoom;
		markDirty();
		return true;
	}

	@Override
	public boolean onMouseMove(int x, int y) {
		InputHandler hover = hovered;
		if (x < x0 || y < y0 || x >= x1 || y >= y1) {
			hovered = null;
			if (hover != null) hover.updateHover();
			return false;
		}
		int gx = floorDiv(x - (x0 + x1 >> 1), scale) - ofsX;
		int gy = floorDiv(y - (y0 + y1 >> 1), scale) - ofsY;
		hovered = editing != null && text.onMouseMove(gx, gy) ? text : null;
		if (hover != hovered) {
			if (hover != null) hover.updateHover();
			if (hovered != null) hovered.updateHover();
		}
		if (gx != mx || gy != my)
			moveSel(gx, gy);
		return true;
	}

	private void moveSel(int x, int y) {
		if (panning) {
			ofsX += x - mx;
			ofsY += y - my;
			markDirty();
			return;
		}
		mx = x; my = y;
		switch(mode) {
		case M_IDLE: {
			int gx = x + 2 >> 2, gy = y + 2 >> 2;
			Trace sel = null;
			Block old = selBlock;
			selBlock = null;
			for (Block block : blocks) {
				for (int i = 0; sel == null && i < block.outs(); i++) {
					Trace t = block.io[i];
					if (t.x() == gx && t.y() == gy) sel = t;
				}
				if (block.isInside(x >> 2, y >> 2)) {
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
			if (selBlock != old) markDirty();
			if (selTr != null) glfwSetCursor(WINDOW, MAIN_CURSOR);
			else if (selBlock == null) glfwSetCursor(WINDOW, SEL_CURSOR);
			else if (selBlock.def.varSize() && (selBlock.y + selBlock.h) * 4 - y < 4)
				glfwSetCursor(WINDOW, VRESIZE_CURSOR);
			else {
				int dy = (y >> 1) - selBlock.textY() >> 2;
				if (dy >= 0 && dy < selBlock.args.length) {
					glfwSetCursor(WINDOW, TEXT_CURSOR);
				} else glfwSetCursor(WINDOW, MAIN_CURSOR);
			}
			return;
		}
		case M_TRACE_SEL:
			glfwSetCursor(WINDOW, MOVE_CURSOR);
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
			glfwSetCursor(WINDOW, MOVE_CURSOR);
			selTr.pos(x + 2 >> 2, y + 2 >> 2);
			return;
		case M_MULTI_SEL:
			glfwSetCursor(WINDOW, SEL_CURSOR);
			markDirty();
			return;
		case M_BLOCK_SCALE:
			glfwSetCursor(WINDOW, VRESIZE_CURSOR);
			if (dSize != (dSize = floorDiv(my - lmy + 2, selBlock.def.model.rh() * 4)))
				markDirty();
			return;
		default: return;
		case M_BLOCK_SEL:
			if (moving.isEmpty())
				moving.add(selBlock.pickup());
		case M_MULTI_MOVE:
			glfwSetCursor(WINDOW, MOVE_CURSOR);
		}
		int dx = x - lmx >> 2, dy = y - lmy >> 2;
		if (dx == 0 && dy == 0) return;
		for (CircuitObject m : moving)
			m.pos(m.x() + dx, m.y() + dy);
		lmx += dx << 2;
		lmy += dy << 2;
	}

	@Override
	public boolean onMouseButton(int button, int action, int mods) {
		parent.focus(this);
		switch(action | button << 1) {
		case GLFW_PRESS | GLFW_MOUSE_BUTTON_RIGHT<<1:
			panning = true;
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_RIGHT<<1:
			panning = false;
			break;
		case GLFW_PRESS | GLFW_MOUSE_BUTTON_LEFT<<1:
			if (hovered == null) focus(null);
			else if (hovered.onMouseButton(button, action, mods)) return true;
			if (selTr == null) {
				if (mode != M_IDLE) break;
				lmx = mx;
				lmy = my;
				if (selBlock == null) mode = M_MULTI_SEL;
				else if (selBlock.def.varSize() && (selBlock.y + selBlock.h) * 4 - my < 4) {
					dSize = 0;
					mode = M_BLOCK_SCALE;
				} else mode = M_BLOCK_SEL;
				markDirty();
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
			if (focused != null && focused.onMouseButton(button, action, mods)) return true;
			if (mode != M_BLOCK_SEL)
				editText(null, -1, 0);
			if (mode == M_MULTI_SEL) {
				int x0 = min(lmx, mx) >> 2, x1 = max(lmx, mx) + 3 >> 2,
				    y0 = min(lmy, my) >> 2, y1 = max(lmy, my) + 3 >> 2;
				for (Trace trace : traces)
					if (trace.pin < 0 && trace.inRange(x0, y0, x1, y1))
						moving.add(trace);
				for (Block block : blocks)
					if (block.inRange(x0, y0, x1, y1))
						moving.add(block);
				for (CircuitObject m : moving) m.pickup();
				for (CircuitObject m : moving) m.pickup();
				lmx = mx;
				lmy = my;
				mode = moving.isEmpty() ? M_IDLE : M_MULTI_MOVE;
				markDirty();
			} else if (mode == M_MULTI_MOVE) {
				for (CircuitObject m : moving)
					m.place();
				moving.clear();
				mode = M_IDLE;
			} else if (mode == M_BLOCK_SEL) {
				Block block = selBlock;
				block.place();
				moving.clear();
				mode = M_IDLE;
				int row = (lmy >> 1) - block.textY() >> 2;
				int col = row >= 0 && row < block.args.length ?
					((lmx >> 1) - block.textX() + block.args[row].length() + 1 >> 1) : 0;
				editText(block, row, col);
				hovered = focused;
				markDirty();
			} else if (mode == M_BLOCK_SCALE) {
				selBlock = selBlock.resize(dSize);
				mode = M_IDLE;
				markDirty();
			} else if (mode == M_TRACE_MV) {
				selTr.place();
				mode = M_IDLE;
			} else if (mode == M_TRACE_SEL)
				if (!selTr.isOut()) {
					mode = M_TRACE_DRAW;
					Trace t = new Trace().pos(mx + 2 >> 2, my + 2 >> 2);
					t.add(this);
					selTr.connect(t);
					selTrace(t);
				} else mode = M_IDLE;
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_MIDDLE<<1:
			int x = mx >> 2, y = my >> 2;
			for (Block block : blocks)
				if (block.isInside(x, y)) {
					addBlock(block.def);
					break;
				}
			break;
		}
		return true;
	}

	@Override
	public boolean onKeyInput(int key, int scancode, int action, int mods) {
		if (super.onKeyInput(key, scancode, action, mods)) return true;
		if (action == GLFW_RELEASE) return false;
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		//boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
		switch(key) {
		case GLFW_KEY_PAGE_UP:
			if (editing != null)
				editText(editing, editArg - 1, text.cursor());
			break;
		case GLFW_KEY_PAGE_DOWN:
			if (editing != null)
				editText(editing, editArg + 1, text.cursor());
			break;
		case GLFW_KEY_DELETE:
			if (selBlock != null)
				selBlock.remove();
			if (selTr != null && selTr.pin < 0)
				selTr.remove();
			for (CircuitObject m : moving) m.remove();
			selBlock = null;
			selTr = null;
			moving.clear();
			mode = M_IDLE;
			break;
		case GLFW_KEY_S:
			if (!ctrl) break;
			String msg = "";
			try {
				BlockDef def = context.def;
				CircuitFile.writeLayout(def, blocks, traces);
				msg = "saved!";
				if (def.assembler instanceof Function m) m.reset();
				else if (def.assembler instanceof ConstList cl)
					cl.compile(ip);
			} catch(IOException e) {
				e.printStackTrace();
				msg = e.toString();
			} catch (SignalError e) {
				lastError = e;
				errorBlock = e.pos >= 0 && e.pos < blocks.size() ? blocks.get(e.pos) : null;
				msg += " " + e.getMessage();
			}
			panel.statusMsg(msg);
			break;
		case GLFW_KEY_W:
			if (ctrl) glfwSetWindowShouldClose(WINDOW, true);
			break;
		case GLFW_KEY_D:
			if (ctrl) cleanUpTraces();
			break;
		case GLFW_KEY_O:
			if (!(ctrl && selBlock != null)) break;
			BlockDef def = selBlock.def;
			Node node = selBlock.outs() > 0 ? selBlock.outs[0] : null;
			while (node != null && node.op instanceof UnpackIns) node = node.in[0].from();
			Value[] ins = new Value[node == null ? 0 : node.in.length];
			Value[] state = result.vars;
			if (state != null)
				for (int i = 0; i < ins.length; i++) {
					Vertex v = node.in[i];
					ins[i] = state[v == null ? 0 : v.addr(0)];
				}
			resolve: while (def.assembler == BasicConstructs.VIRTUAL) {
				if (ins.length == 1) {
					if (ins[0] == null) break;
					BlockDef def1 = ins[0].type.get(def.id);
					if (def1 != null) {
						def = def1;
						continue resolve;
					}
				} else for (int i = 0; i < ins.length; i++) {
					if (ins[i] == null) continue;
					BlockDef def1 = ins[i].type.get(def.id + "@" + i);
					if (def1 != null) {
						def = def1;
						continue resolve;
					}
				}
				break;
			}
			if (def.assembler instanceof Function f) {
				open(f.def);
				System.arraycopy(ins, 0, context.env, 0, ins.length);
			}
			break;
		case GLFW_KEY_HOME:
			ofsX = ofsY = 0;
			markDirty();
			break;
		}
		return true;
	}

	boolean selTrace(Trace t) {
		if (t == selTr) return false;
		selTr = t;
		StringBuilder sb = new StringBuilder();
		if (t != null && t.block != null && t.pin >= 0) {
			BlockDef def = t.block.def;
			String[] names = t.isOut() ? def.outs : def.ins;
			int i = t.isOut() ? t.pin : t.pin - t.block.outs();
			i = min(i, names.length - 1);
			sb.append(i < 0 ? "?" : names[i]).append(": ");
		}
		if (t != null) {
			Value v = t.value(result.vars);
			if (v != null) sb.append(v.toString());
			else sb.append("[not evaluated]");
		}
		int l = min(sb.length(), (x1 - y0) / 8);
		markDirty();
		panel.statusMsg(sb.substring(0, l));
		return true;
	}

	public void addBlock(BlockDef type) {
		editText(null, -1, 0);
		lmx = mx; lmy = my;
		selBlock = new Block(type, 1);
		selBlock.pos((mx >> 1) + 1 - selBlock.w >> 1, (my >> 1) + 1 - selBlock.h >> 1);
		selBlock.add(this);
		Trace t = selBlock.io[0];
		if (selTr != null && t.isOut())
			(selTr.pin >= 0 ? selTr : selTr.to).connect(t);
		selTrace(t);
		mode = M_BLOCK_SEL;
	}

	@Override
	public void close(long window) {
		super.close(window);
		glDeleteBuffers(blockVAO.buffer);
		glDeleteBuffers(traceVAO.buffer);
		ip.terminate();
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
		markDirty();
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
			tr.remove();
			if (f != null && f.pin < 0 && f.to == null)
				toRemove.add(f);
		}
		panel.statusMsg("removed " + n + " traces!");
	}

	@Override
	public void useBlock(BlockDef def, int mb) {
		switch(mb) {
		case GLFW_MOUSE_BUTTON_LEFT -> addBlock(def);
		case GLFW_MOUSE_BUTTON_RIGHT -> open(def);
		}
	}

}
