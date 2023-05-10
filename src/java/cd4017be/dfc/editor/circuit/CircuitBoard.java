package cd4017be.dfc.editor.circuit;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static cd4017be.dfc.lang.LoadingCache.IN_BLOCK;
import static cd4017be.dfc.lang.LoadingCache.OUT_BLOCK;
import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.floorDiv;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import cd4017be.dfc.editor.Main;
import cd4017be.dfc.editor.Palette;
import cd4017be.dfc.editor.gui.Drawable;
import cd4017be.dfc.editor.gui.GuiGroup;
import cd4017be.dfc.editor.gui.InputHandler;
import cd4017be.dfc.editor.gui.Label;
import cd4017be.dfc.editor.gui.TextField;
import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.CircuitFile;
import cd4017be.dfc.lang.CircuitFile.Layout;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.NodeContext;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;
import cd4017be.dfc.lang.Interpreter.Task;
import cd4017be.dfc.lang.Node.Vertex;
import cd4017be.dfc.lang.builders.BasicConstructs;
import cd4017be.dfc.lang.builders.ConstList;
import cd4017be.dfc.lang.builders.Function;
import cd4017be.dfc.lang.builders.Macro;
import cd4017be.dfc.lang.instructions.UnpackIns;
import cd4017be.util.IndexedSet;
import cd4017be.util.VertexArray;

/**
 * @author cd4017be */
public class CircuitBoard implements InputHandler, Drawable {

	/**Editing modes */
	private static final byte M_IDLE = 0, M_BLOCK_SEL = 1, M_TRACE_SEL = 2, M_TRACE_MV = 3, M_TRACE_DRAW = 4,
	M_MULTI_SEL = 5, M_MULTI_MOVE = 6, M_BLOCK_SCALE = 7;

	public final IndexedSet<Block> blocks;
	public final IndexedSet<Trace> traces;
	private final ArrayList<CircuitObject> moving = new ArrayList<>();
	/** GL vertex arrays */
	final VertexArray blockVAO, traceVAO;
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
	//final TextField text = new TextField(this::setText);
	public final GuiGroup gui;
	TextField text;
	Label info;
	Palette palette;
	final Interpreter ip;
	NodeContext context;
	Task result;

	public CircuitBoard(GuiGroup gui) {
		this.gui = gui;
		gui.add(this);
		this.blockVAO = genBlockVAO(64);
		this.traceVAO = genTraceVAO(64);
		this.blocks = new IndexedSet<>(new Block[64]);
		this.traces = new IndexedSet<>(new Trace[64]);
		this.ip = new Interpreter();
		glUseProgram(traceP);
		glUniform2f(trace_lineSize, 1F, 1F);
		checkGLErrors();
	}

	@Override
	public void redraw() {
		if (reRunTypecheck) {
			context.typeCheck(ip, blocks, task -> Main.runAsync(() -> applyResult(task)));
			reRunTypecheck = false;
		}
		
		float sx = 2F / (gui.x1 - gui.x0) * zoom;
		float sy = -2F / (gui.y1 - gui.y0) * zoom;
		float ox = (float)ofsX * sx * 0.5F;
		float oy = (float)ofsY * sx * 0.5F;
		
		TRACES.bind();
		transform(trace_transform, ox, oy, sx, sy);
		traceVAO.count = traces.size() * 4;
		traceVAO.draw();
		checkGLErrors();
		
		ICONS.bind();
		transform(block_transform, ox, oy, sx, sy);
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
				h += dSize * selBlock.def.model.rh() * 2;
				String s = "size " + (dSize + selBlock.size());
				print(s, FG_BLUE_L, selBlock.x * 2 + selBlock.w - s.length(), selBlock.y * 2 + h, 2, 3);
			}
			addSel(selBlock.x * 2, selBlock.y * 2, selBlock.w * 2, h, FG_BLUE_L);
		}
		if (selTr != null)
			addSel(selTr.x() * 2 - 1, selTr.y() * 2 - 1, 2, 2, FG_RED_L);
		drawSel(ox, oy, sx * 0.5F, sy * 0.5F, 0F, 1F);
		for (Block block : blocks) block.printText();
		drawText(ofsX, oy + sy * .25F, sx * 0.5F, sy * 0.5F);
	}

	@Override
	public boolean onScroll(double dx, double dy) {
		if (dy == 0) return false;
		int zoom1 = max(4, zoom + (dy > 0 ? 4 : -4));
		if (zoom1 == zoom) return true;
		float scale = (float)zoom / (float)zoom1;
		float dox = ofsX + mx, doy = ofsY + my;
		ofsX = round(dox * scale) - mx;
		ofsY = round(doy * scale) - my;
		zoom = zoom1;
		gui.markDirty();
		return true;
	}

	@Override
	public boolean onMouseMove(int x, int y) {
		int gx = x / zoom - ofsX;
		int gy = y / zoom - ofsY;
		if (gx != mx || gy != my)
			moveSel(gx, gy);
		return true;
	}

	private void moveSel(int x, int y) {
		if (panning) {
			ofsX += x - mx;
			ofsY += y - my;
			gui.markDirty();
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
			if (selBlock != old) gui.markDirty();
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
			selTr.pos(x + 1 >> 1, y + 1 >> 1);
			return;
		case M_MULTI_SEL:
			glfwSetCursor(WINDOW, SEL_CURSOR);
			gui.markDirty();
			return;
		case M_BLOCK_SCALE:
			glfwSetCursor(WINDOW, VRESIZE_CURSOR);
			if (dSize != (dSize = floorDiv(my - lmy + 2, selBlock.def.model.rh() * 2)))
				gui.markDirty();
			return;
		default: return;
		case M_BLOCK_SEL:
			if (moving.isEmpty())
				moving.add(selBlock.pickup());
		case M_MULTI_MOVE:
			glfwSetCursor(WINDOW, MOVE_CURSOR);
		}
		int dx = x - lmx >> 1, dy = y - lmy >> 1;
		if (dx == 0 && dy == 0) return;
		for (CircuitObject m : moving)
			m.pos(m.x() + dx, m.y() + dy);
		lmx += dx << 1;
		lmy += dy << 1;
	}

	@Override
	public boolean onMouseButton(int button, int action, int mods) {
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
				gui.markDirty();
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
				for (CircuitObject m : moving) m.pickup();
				for (CircuitObject m : moving) m.pickup();
				lmx = mx;
				lmy = my;
				mode = moving.isEmpty() ? M_IDLE : M_MULTI_MOVE;
				gui.markDirty();
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
				int row = lmy - block.textY() >> 2;
				int col = row >= 0 && row < block.args.length ?
					(lmx - block.textX() + block.args[row].length() + 1 >> 1) : 0;
				editText(block, row, col);
				gui.markDirty();
			} else if (mode == M_BLOCK_SCALE) {
				selBlock = selBlock.resize(dSize);
				mode = M_IDLE;
				gui.markDirty();
			} else if (mode == M_TRACE_MV) {
				selTr.place();
				mode = M_IDLE;
			} else if (mode == M_TRACE_SEL)
				if (!selTr.isOut()) {
					mode = M_TRACE_DRAW;
					Trace t = new Trace().pos(mx + 1 >> 1, my + 1 >> 1);
					t.add(this);
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
		if (panning || mode != M_IDLE && mode != M_TRACE_DRAW)
			gui.focus(this);
		return true;
	}

	@Override
	public boolean onKeyInput(int key, int scancode, int action, int mods) {
		if (action == GLFW_RELEASE) return false;
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		//boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
		switch(key) {
		case GLFW_KEY_PAGE_UP:
			if (editing == null) return false;
			editText(editing, editArg - 1, text.cursor());
			return true;
		case GLFW_KEY_PAGE_DOWN:
			if (editing == null) return false;
			editText(editing, editArg + 1, text.cursor());
			return true;
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
			return true;
		case GLFW_KEY_S:
			if (!ctrl) return false;
			try {
				BlockDef def = context.def;
				CircuitFile.writeLayout(def, blocks, traces);
				info.text("saved!");
				if (def.assembler instanceof Function m) m.reset();
				else if (def.assembler instanceof ConstList cl)
					cl.compile(ip);
			} catch(IOException e) {
				e.printStackTrace();
				info.text(e.toString());
			} catch (SignalError e) {
				lastError = e;
				errorBlock = e.pos >= 0 && e.pos < blocks.size() ? blocks.get(e.pos) : null;
				info.text(info.text + " " + e.getMessage());
			}
			return true;
		case GLFW_KEY_W:
			if (!ctrl) return false;
			glfwSetWindowShouldClose(WINDOW, true);
			return true;
		case GLFW_KEY_D:
			if (!ctrl) return false;
			cleanUpTraces();
			return true;
		case GLFW_KEY_O:
			if (!(ctrl && selBlock != null)) return false;
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
			return true;
		case GLFW_KEY_HOME:
			ofsX = ofsY = 0;
			gui.markDirty();
			return true;
		default:
			return false;
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
			i = min(i, names.length - 1);
			sb.append(i < 0 ? "?" : names[i]).append(": ");
		}
		if (t != null) {
			Value v = t.value(result.vars);
			if (v != null) sb.append(v.toString());
			else sb.append("[not evaluated]");
		}
		int l = min(sb.length(), (gui.x1 - gui.y0) / 8);
		info.text(sb.substring(0, l));
		return true;
	}

	public void addBlock(BlockDef type) {
		editText(null, -1, 0);
		lmx = mx; lmy = my;
		selBlock = new Block(type, 1);
		selBlock.pos(mx + 1 - selBlock.w >> 1, my + 1 - selBlock.h >> 1);
		selBlock.add(this);
		Trace t = selBlock.io[0];
		if (selTr != null && t.isOut())
			(selTr.pin >= 0 ? selTr : selTr.to).connect(t);
		selTrace(t);
		mode = M_BLOCK_SEL;
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
				for (Trace trace : layout.traces()) trace.add(this);
				for (Block block : layout.blocks()) block.add(this);
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

	private void clear() {
		editText(null, -1, 0);
		moving.clear();
		blocks.clear();
		traces.clear();
		mode = M_IDLE;
		selBlock = errorBlock = null;
		selTr = null;
		lastError = null;
		gui.markDirty();
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
		info.text("removed " + n + " traces!");
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
			info.text(lastError.getMessage());
		}
		if (task.vars != null)
			for (Block block : blocks)
				block.updateColors(task.vars);
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
			if (editing != null) gui.markDirty();
			updateArg();
			editing = null;
			editArg = -1;
			text.text("");
			text.autoComplete.clear();
		} else {
			if (block != editing || row != editArg) {
				updateArg();
				editing = block;
				editArg = row;
				text.autoComplete.clear();
				if (context.def.assembler instanceof Macro)
					for (String s : context.def.args)
						text.autoComplete.add(s);
				block.def.assembler.getAutoCompletions(block, row, text.autoComplete, context);
				Collections.sort(text.autoComplete);
				gui.markDirty();
			}
			text.text(block.args[row]);
			text.cursor(col);
		}
	}

}
