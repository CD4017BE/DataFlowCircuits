package cd4017be.dfc.editor;

import java.io.*;
import java.util.*;
import cd4017be.compiler.Context;
import cd4017be.compiler.MacroState;
import cd4017be.compiler.MutableMacro;
import cd4017be.compiler.NodeState;
import cd4017be.compiler.Signal;
import cd4017be.compiler.SignalError;
import cd4017be.dfc.compiler.CompileError;
import cd4017be.dfc.compiler.Compiler;
import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.BlockRegistry;
import cd4017be.dfc.lang.CircuitFile;
import cd4017be.util.*;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.*;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL20C.*;

/**Implements the circuit editor user interface.
 * @author CD4017BE */
public class CircuitEditor implements IGuiSection {

	/**Editing modes */
	private static final byte M_IDLE = 0, M_BLOCK_SEL = 1, M_TRACE_SEL = 2, M_TRACE_MV = 3, M_TRACE_DRAW = 4,
	M_MULTI_SEL = 5, M_MULTI_MOVE = 6;

	public final IndexedSet<Block> blocks;
	public final HashMap<Integer, Trace> traces;
	final ArrayList<IMovable> moving = new ArrayList<>();
	final BlockIcons icons;
	final BlockRegistry reg;
	final MacroEdit edit;
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

	public CircuitEditor(MacroEdit edit, Palette pal, BlockRegistry reg) {
		this.blockVAO = genBlockVAO(64);
		this.traceVAO = genTraceVAO(64);
		this.blocks = new IndexedSet<>(new Block[16]);
		this.traces = new HashMap<>();
		this.context = new Context(reg);
		this.icons = pal.icons;
		this.reg = reg;
		this.edit = edit;
		pal.circuit = this;
		this.macro = new MutableMacro(edit.getDef());
		
		fullRedraw();
		glUseProgram(traceP);
		glUniform2f(trace_lineSize, 0.25F, 1F);
		checkGLErrors();
		
		load(edit.curFile);
		if (blocks.isEmpty()) {
			BlockDef def = edit.getDef(),
			out = reg.get(BlockDef.OUT_ID),
			in = reg.get(BlockDef.IN_ID);
			for (int o = def.outCount, i = 0; i < def.ios(); i++) {
				Block block = new Block(i < o ? out : in, this);
				block.setText(def.ioNames[i]);
				block.pos(
					i < o ? 2 : -2 - block.w(),
					(i < o ? i : i - o) * 4
				).place();
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
					addSel(block.x * 2, block.y * 2, block.w() * 2, block.h() * 2, FG_RED);
			}
			for (NodeState ns = ms.first; ns != null; ns = ns.next) {
				Block block = macro.blocks[ns.node.idx];
				if (block != null)
					addSel(block.x * 2, block.y * 2, block.w() * 2, block.h() * 2, FG_GREEN);
			}
		}
		if (mode == M_MULTI_SEL || mode == M_MULTI_MOVE)
			addSel(min(lmx, mx), min(lmy, my), abs(mx - lmx), abs(my - lmy), FG_GREEN_L);
		if (selBlock != null)
			addSel(selBlock.x * 2, selBlock.y * 2, selBlock.w() * 2, selBlock.h() * 2, FG_BLUE_L);
		if (selTr != null)
			addSel(selTr.x() * 2 - 1, selTr.y() * 2 - 1, 2, 2, FG_RED_L);
		drawSel(ofsX, -ofsY, 0.5F * scaleX, -0.5F * scaleY, 0F, 1F);
		
		if (text != null && selBlock != null)
			text.redraw(selBlock.textX(), selBlock.textY(), 4, 6, 0);
		for (Block block : blocks) {
			if (block.text().isBlank()) continue;
			print(block.text(), FG_YELLOW_L, block.textX(), block.textY(), 4, 6);
		}
		drawText(ofsX, -ofsY, scaleX * 0.25F, scaleY * -0.25F);
		print(info, FG_YELLOW_L, 0, -1, 1, 1);
		drawText(-1F, -1F, 32F / (float)WIDTH, -48F / (float)HEIGHT);
	}

	private void redrawTraces() {
		MacroState ms = macro.state;
		traceVAO.clear();
		for (Trace t : traces.values())
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
			selTrace(traces.get(Trace.key(x + 1 >> 1, y + 1 >> 1)));
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
				for (Trace trace : traces.values())
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
				if (block.def.hasArg) {
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
					addBlock(reg.get(block.def.name));
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
			if (edit.curFile != null && !shift) save(edit.curFile);
			else new FileBrowser(FILE, "Save as ", CircuitFile.FILTER, this::save);
			break;
		case GLFW_KEY_O:
			if (ctrl) new FileBrowser(FILE, "Open ", CircuitFile.FILTER, this::load);
			break;
		case GLFW_KEY_N:
			if (!ctrl) break;
			clear();
			edit.curFile = null;
			setTitle(null);
			break;
		case GLFW_KEY_H:
			if (!ctrl || edit.curFile == null) break;
			context.clear();
			try {
				info = loadHeader(edit.curFile, !shift)
					? "refreshed external declarations!"
					: "current circuit has no .c file!";
			} catch(IOException e) {
				e.printStackTrace();
				info = e.getMessage();
			}
			refresh(0);
			break;
		case GLFW_KEY_W:
			if (ctrl) glfwSetWindowShouldClose(WINDOW, true);
			break;
		case GLFW_KEY_D:
			if (ctrl) cleanUpTraces();
			break;
		case GLFW_KEY_M:
			if (!ctrl) break;
			if (edit.curFile != null) compile(edit.curFile, shift);
			else new FileBrowser(FILE, "Compile to ", n -> n.endsWith(".ll"), f -> compile(f, shift));
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
			sb.append(t.block.def.ioNames[t.pin]).append(": ");
		}
		while(t != null && !t.isOut()) t = t.from;
		if (t != null && t.block != null) {
			Signal sg = t.block.signal(t.pin);
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
		selBlock = new Block(type, this)
		.pos(mx + 1 - type.icon.w >> 1, my + 1 - type.icon.h >> 1);
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
		context.clear();
	}

	private void cleanUpTraces() {
		ArrayList<Trace> toRemove = new ArrayList<>();
		for (Trace tr : traces.values())
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

	private boolean loadHeader(File file, boolean cpp) throws IOException {
//		File h = withSuffix(file, ".c");
//		if (!h.exists()) return false;
//		Profiler t = new Profiler(System.out);
//		HeaderParser hp = new HeaderParser();
//		hp.processHeader(h, cpp);
//		t.end("header preprocessed");
//		hp.getDeclarations(context.extDef);
//		t.end("header parsed");
		return true;
	}

	private void compile(File file, boolean debug) {
		file = withSuffix(file, ".ll");
		try {
			Profiler t = new Profiler(System.out);
			Compiler c = new Compiler(null);
			t.end("sequentialized");
			c.resolveIds();
			t.end("resolved variables");
			try (FileWriter fw = new FileWriter(file, US_ASCII)){
				c.assemble(fw, debug);
				t.end("written");
				info = "compiled!";
			} catch(IOException e) {
				e.printStackTrace();
				info = e.toString();
			}
		} catch(CompileError e) {
			selBlock = e.idx >= 0 ? blocks.get(e.idx) : null;
			info = "Error: " + e.getMessage();
		}
		refresh(0);
	}

	public static final File FILE = new File("./test");

	public static File withSuffix(File file, String sfx) {
		String name = file.getName();
		if (name.endsWith(sfx)) return file;
		int i = name.lastIndexOf(sfx.charAt(0));
		name = (i < 0 ? name : name.substring(0, i)).concat(sfx);
		return new File(file.getParent(), name);
	}

	private void load(File file) {
		if (file == null) return;
		setTitle((edit.curFile = file).getName());
		try(CircuitFile cf = new CircuitFile(file.toPath(), false)) {
			clear();
			cf.readLayout(this, cf.readCircuit(reg));
			loadHeader(file, true);
			info = "loaded!";
		} catch(IOException e) {
			e.printStackTrace();
			info = e.toString();
		}
		reg.load();
		refresh(0);
	}

	private void save(File file) {
		if (file == null) return;
		edit.curFile = file = withSuffix(file, ".dfc");
		setTitle(file.getName());
		try {
			try(CircuitFile out = new CircuitFile(file.toPath(), true)) {
				out.writeLayout(blocks);
				out.writeHeader();
			}
			info = "saved!";
		} catch (IOException e) {
			e.printStackTrace();
			info = e.toString();
		}
		refresh(0);
	}

	ArrayDeque<Trace> traceUpdates = new ArrayDeque<>();

	private void updateTypeCheck() {
		while(!traceUpdates.isEmpty())
			traceUpdates.remove().update();
		if (context.tick(1000)) {
			//TODO repeated frame updates
		}
	}

}
