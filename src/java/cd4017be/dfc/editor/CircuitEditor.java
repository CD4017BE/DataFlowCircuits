package cd4017be.dfc.editor;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.compiler.CompileError;
import cd4017be.dfc.compiler.Compiler;
import cd4017be.dfc.graph.*;
import cd4017be.dfc.graph.Node;
import cd4017be.dfc.lang.*;
import cd4017be.util.*;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static cd4017be.dfc.graph.Circuit.SEP;
import static java.lang.Math.*;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;

/**Implements the circuit editor user interface.
 * @author CD4017BE */
public class CircuitEditor implements IGuiSection, Macro {

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
	final int blockVAO, traceVAO;
	/** GL buffers */
	final int blockBuf, traceBuf;
	/** draw counters */
	int traceCount;
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

	public CircuitEditor(MacroEdit edit, Palette pal, BlockRegistry reg) {
		this.blockVAO = genBlockVAO(blockBuf = glGenBuffers());
		glBufferData(GL_ARRAY_BUFFER, 256 * BLOCK_STRIDE, GL_DYNAMIC_DRAW);
		this.traceVAO = genTraceVAO(traceBuf = glGenBuffers());
		allocSelBuf(2);
		this.blocks = new IndexedSet<>(new Block[16]);
		this.traces = new HashMap<>();
		this.context = new Context(reg);
		this.icons = pal.icons;
		this.reg = reg;
		this.edit = edit;
		pal.circuit = this;
		initGraph(edit.getDef());
		
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

	public void reserveBlockBuf(int size) {
		size *= BLOCK_STRIDE;
		glBindBuffer(GL_ARRAY_BUFFER, blockBuf);
		int l = glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
		if (l >= size) return;
		glBufferData(GL_ARRAY_BUFFER, max(size, l << 1), GL_DYNAMIC_DRAW);
		for (Block block : blocks) block.redraw();
	}

	@Override
	public void onResize(int w, int h) {
	}

	@Override
	public void redraw() {
		updateTypeCheck();
		if (icons.update() || redraw < 0) redrawTraces();
		redraw = 0;
		//draw frame
		float scaleX = (float)(zoom * 2) / (float)WIDTH;
		float scaleY = (float)(zoom * 2) / (float)HEIGHT;
		float ofsX = (float)this.ofsX * scaleX * 0.5F;
		float ofsY = (float)this.ofsY * scaleY * 0.5F;
		float[] mat = new float[] {
			scaleX,   0, 0, 0,
			0, -scaleY, 0, 0,
			ofsX,-ofsY, 0, 1F
		};
		icons.bind();
		glUseProgram(traceP);
		glUniformMatrix3x4fv(trace_transform, false, mat);
		glBindVertexArray(traceVAO);
		glDrawArrays(GL_LINE_STRIP, 0, traceCount);
		checkGLErrors();
		
		glUseProgram(blockP);
		glUniformMatrix3x4fv(block_transform, false, mat);
		glBindVertexArray(blockVAO);
		glDrawArrays(GL_POINTS, 0, blocks.size());
		checkGLErrors();
		
		ArrayList<SignalError> errors = new ArrayList<>();
		for (SignalError e = context.errors.next; e != null; e = e.next) {
			Node node = e.node;
			if (node != null && node.macro == this && node.idx >= 0 && node.idx < blocks.size())
				errors.add(e);
		}
		allocSelBuf(3 + errors.size());
		Node node = context.firstUpdate;
		if (node != null && node.idx >= 0 && node.idx < blocks.size()) {
			Block block = blocks.get(node.idx);
			addSel(block.x * 2, block.y * 2, block.w() * 2, block.h() * 2, 0xff00ff00);
		}
		errors: for (SignalError e : errors) {
			node = e.node;
			int io = e.io;
			while (node.macro != this) {
				if (node.macro == null || (node = node.macro.parent()) == null)
					continue errors;
				io = Integer.MAX_VALUE;
			}
			Block block = blocks.get(node.idx);
			if (io < block.io.length) {
				Trace tr = block.io[io];
				addSel(tr.x() * 2 - 1, tr.y() * 2 - 1, 2, 2, 0xffff0000);
			} else addSel(block.x * 2, block.y * 2, block.w() * 2, block.h() * 2, 0xffff0000);
		}
		if (mode == M_MULTI_SEL || mode == M_MULTI_MOVE)
			addSel(min(lmx, mx), min(lmy, my), abs(mx - lmx), abs(my - lmy), 0xff80ff80);
		if (selBlock != null)
			addSel(selBlock.x * 2, selBlock.y * 2, selBlock.w() * 2, selBlock.h() * 2, 0xff8080ff);
		if (selTr != null)
			addSel(selTr.x() * 2 - 1, selTr.y() * 2 - 1, 2, 2, 0xffff8080);
		drawSel(ofsX, -ofsY, 0.5F * scaleX, -0.5F * scaleY, 0F, 1F, 0x00000000);
		
		startText();
		float sx = scaleX, sy = -1.5F * scaleY;
		if (text != null)
			text.redraw(
				ofsX, -ofsY, sx, sy, 256,
				0xffffff80, 0xffffff80, 0xffff8080
			);
		for (Block block : blocks) {
			if (block.text().isBlank()) continue;
			print(
				block.text(), 256, 0xffffff80, 0x00000000,
				block.textX() / 4F * sx + ofsX,
				block.textY() / 6F * sy - ofsY,
				sx, sy
			);
		}
		sx = 32F / (float)WIDTH;
		sy = -48F / (float)HEIGHT;
		print(info, 256, 0xffffff80, 0x00000000, -1F, -1F - sy, sx, sy);
		
		glBindVertexArray(0);
	}

	private void redrawTraces() {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(traces.size() * 2 * TRACE_STRIDE);
			for (Trace t : traces.values()) {
				Trace t0 = t.from;
				if (t0 == null) {
					t0 = t; t = t.to;
					if (t == null) continue;
				} else if (t0.to == t && (t0.placed || t0.from != null))
					continue;
				t0.draw(buf, true);
				for (t0 = t; t0 != null; t0 = t0.to)
					t0.draw(buf, false);
			}
			traceCount = buf.position() / TRACE_STRIDE;
			glBindBuffer(GL_ARRAY_BUFFER, traceBuf);
			glBufferData(GL_ARRAY_BUFFER, buf.flip(), GL_DYNAMIC_DRAW);
		}
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
					if (block.node != null && block.node.error != null)
						info = block.node.error.getLocalizedMessage();
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
					int tx = block.textX();
					text = new TextField(t -> {
						block.setText(t);
						block.redraw();
						if (block.node != null)
							if (block.def.name.equals(BlockDef.IN_ID))
								block.node.connect(0, null, context);
							else context.updateNode(block.node, 0);
					}, tx / 4F, block.textY() / 6F);
					text.set(block.text(), 1 + (lmx * 2 - tx >> 2));
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
			for (Block block : blocks)
				createNode(block);
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
		while(t != null && t.pin > 0) t = t.from;
		if (t != null && t.block != null) {
			Signal sg = t.block.signal();
			if (sg != null) sg.displayString(sb, true);
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
		reserveBlockBuf(blocks.size() + 1);
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
	 * @param ofs byte offset in vertex buffer
	 * @param x
	 * @param y */
	public void updatePos(int ofs, int x, int y) {
		if (redraw < 0) return;
		Main.refresh(0);
		redraw |= 1;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			glBindBuffer(GL_ARRAY_BUFFER, traceBuf);
			glBufferSubData(GL_ARRAY_BUFFER, ofs, ms.shorts((short)x, (short)y));
			checkGLErrors();
		}
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
		glDeleteVertexArrays(blockVAO);
		glDeleteVertexArrays(traceVAO);
		glDeleteBuffers(blockBuf);
		glDeleteBuffers(traceBuf);
	}

	private void clear() {
		blocks.clear();
		traces.clear();
		context.clear();
	}

	private void cleanUpTraces() {
		ArrayList<Trace> toRemove = new ArrayList<>();
		for (Trace tr : traces.values())
			if (tr.pin < 0 && (tr.block == null || tr.to == null))
				toRemove.add(tr);
		int n = 0;
		while(!toRemove.isEmpty()) {
			n++;
			Trace tr = toRemove.remove(toRemove.size() - 1);
			Trace f = tr.from;
			tr.remove();
			if (f != null && f.pin < 0 && f.to == null)
				toRemove.add(f);
		}
		info = "removed " + n + " traces!";
		refresh(0);
	}

	private boolean loadHeader(File file, boolean cpp) throws IOException {
		File h = withSuffix(file, ".c");
		if (!h.exists()) return false;
		Profiler t = new Profiler(System.out);
		HeaderParser hp = new HeaderParser();
		hp.processHeader(h, cpp);
		t.end("header preprocessed");
		hp.getDeclarations(context.extDef);
		t.end("header parsed");
		return true;
	}

	private void compile(File file, boolean debug) {
		file = withSuffix(file, ".ll");
		try {
			Profiler t = new Profiler(System.out);
			Compiler c = new Compiler(getOutput(context));
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

	HashMap<String, Block> outputs = new HashMap<>();
	ArrayDeque<Trace> traceUpdates = new ArrayDeque<>();
	Node parent;
	HashMap<String, Integer> links = new HashMap<>();
	String[] args;
	int argCount, extraArgs;

	private void initGraph(BlockDef def) {
		this.parent = new Root(def).getOutput(context);
		links.clear();
		if (def.hasArg) {
			String[] args = SEP.split(def.ioNames[def.ioNames.length - 1]);
			for (int i = 0; i < args.length; i++)
				links.put(args[i], i);
			this.argCount = args.length;
		} else this.argCount = 0;
		this.args = parent.arguments(argCount);
		this.extraArgs = args.length - argCount;
		parent.data = this;
	}

	private void updateTypeCheck() {
		while(!traceUpdates.isEmpty()) {
			Trace tr = traceUpdates.remove();
			for (Trace to = tr.to; to != null; to = to.adj)
				traceUpdates.add(to);
			Block block = tr.block;
			if (block == null) continue;
			int in = tr.pin - block.def.outCount;
			if (in >= 0 && block.node != null)
				block.node.connect(in, null, context);
		}
		if (context.tick(1000)) {
			//TODO repeated frame updates
		}
	}

	private String resolveArg(String arg) {
		int idx = links.getOrDefault(arg, -1);
		return idx < 0 ? arg : args[idx];
	}

	public Node createNode(Block block) {
		BlockDef def = block.def;
		Node node = new Node(
			this, block.getIdx(), def,
			BlockDef.IN_ID.equals(def.name) ? 2 : 1 + def.inCount
		);
		context.updateNode(node, 0);
		return block.node = node;
	}

	private Node getNode(Block block) {
		if (block == null) return Node.NULL;
		if (block.node == null) return createNode(block);
		return block.node.data instanceof Macro m
			? m.getOutput(context) : block.node;
	}

	@Override
	public Node getOutput(Context c) {
		return getNode(outputs.get(parent.def.ioNames[0]));
	}

	@Override
	public void connectInput(Node n, int i, Context c) {
		Node src = Node.NULL;
		Block block = blocks.get(n.idx);
		if (block.def.name.equals(BlockDef.IN_ID)) {
			String name = resolveArg(block.text());
			block = outputs.get(name);
			if (block != null) src = getNode(block);
			else {
				BlockDef def = parent.def;
				String[] names = def.ioNames;
				for (int k = 0, j = def.outCount; k < def.inCount; j++, k++)
					if (names[j].equals(name)) {
						src = parent.getInput(k, c);
						break;
					}
			}
		} else {
			Trace t = block.io[i + block.def.outCount];
			while(t.from != null) t = t.from;
			if (t.isOut()) src = getNode(t.block);
		}
		n.connect(i, src, c);
	}

	@Override
	public String[] arguments(Node node, int min) {
		String s = blocks.get(node.idx).text();
		int[] argbuf = new int[254];
		int n = CircuitFile.parseArgument(s, argbuf);
		String last = s.substring(n == 0 ? 0 : argbuf[n - 1] + 1).trim();
		String[] res;
		int l = n + 1;
		if (l > 0 && extraArgs > 0 && links.getOrDefault(last, -1) == argCount - 1) {
			int m = extraArgs + l;
			if (m >= min) res = new String[m];
			else Arrays.fill(res = new String[min], m, min, "");
			System.arraycopy(args, argCount, res, l, extraArgs);
		} else if (l >= min) res = new String[l];
		else Arrays.fill(res = new String[min], l, min, "");
		for (int i = 0, p = 0; i < l; i++, p++) {
			String arg = i == n ? last : s.substring(p, p = argbuf[i]).trim();
			int id = links.getOrDefault(arg, -1);
			res[i] = id >= 0 ? args[id] : arg;
		}
		return res;
	}

	@Override
	public Node parent() {
		return parent;
	}

}
