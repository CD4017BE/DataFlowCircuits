package cd4017be.dfc.editor;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;
import java.util.zip.DataFormatException;

import org.lwjgl.system.MemoryStack;
import cd4017be.dfc.compiler.Instruction;
import cd4017be.dfc.lang.*;
import cd4017be.util.*;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.*;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;

/**Implements the circuit editor user interface.
 * @author CD4017BE */
public class Circuit implements IGuiSection, Function<String, BlockDef> {

	/**Editing modes */
	private static final byte M_IDLE = 0, M_BLOCK_SEL = 1, M_TRACE_SEL = 2, M_TRACE_MV = 3, M_TRACE_DRAW = 4;

	public final IndexedSet<Block> blocks;
	public final HashMap<Integer, Trace> traces;
	final BlockIcons icons;
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
	File curFile;
	final ExternalDefinitions extDef;

	public Circuit(Palette pal) {
		this.blockVAO = genBlockVAO(blockBuf = glGenBuffers());
		glBufferData(GL_ARRAY_BUFFER, 256 * BLOCK_STRIDE, GL_DYNAMIC_DRAW);
		this.traceVAO = genTraceVAO(traceBuf = glGenBuffers());
		allocSelBuf(2);
		this.blocks = new IndexedSet<>(new Block[16]);
		this.traces = new HashMap<>();
		this.extDef = new ExternalDefinitions();
		this.icons = pal.icons;
		pal.circuit = this;
		
		fullRedraw();
		glUseProgram(traceP);
		glUniform2f(trace_lineSize, 0.25F, 1F);
		checkGLErrors();
		
		new Block(icons.get(BlockDef.OUT_ID), this).pos(0, 0).place();
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
			if (block.data.isBlank()) continue;
			print(
				block.data, 256, 0xffffff80, 0x00000000,
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
		if (mode == M_IDLE) {
			selTrace(traces.get(Trace.key(x + 1 >> 1, y + 1 >> 1)));
			Block old = selBlock;
			selBlock = null;
			for (Block block : blocks)
				if (block.isInside(x >> 1, y >> 1)) {
					selBlock = block;
					break;
				}
			if (selBlock != old) refresh(0);
		} else if (mode == M_TRACE_SEL && selTr.pin < 0) {
			selTr.pickup();
			mode = M_TRACE_MV;
		}
		if (mode == M_TRACE_MV || mode == M_TRACE_DRAW)
			selTr.pos(x + 1 >> 1, y + 1 >> 1);
		else if (mode == M_BLOCK_SEL && selBlock != null) {
			int dx = x - lmx >> 1, dy = y - lmy >> 1;
			selBlock.pickup().pos(selBlock.x + dx, selBlock.y + dy);
			lmx += dx << 1; lmy += dy << 1;
		}
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
				mode = M_BLOCK_SEL;
				refresh(0);
			} else if (mode == M_TRACE_DRAW) {
				Trace t = selTr.place();
				if (selTrace(t)) mode = M_IDLE;
				else mode = M_TRACE_SEL;
			} else if (mode == M_IDLE && selTr.pin != 0)
				mode = M_TRACE_SEL;
			else if (mode == M_BLOCK_SEL && selBlock != null) {
				selBlock.place();
				mode = M_IDLE;
			}
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_LEFT<<1:
			if (mode == M_BLOCK_SEL) {
				Block block = selBlock;
				if (block != null) {
					block.place();
					if (block.def.hasText) {
						int tx = block.textX();
						text = new TextField(t -> {
							block.data = t;
							block.redraw();
						}, tx / 4F, block.textY() / 6F);
						text.set(block.data, 1 + (lmx * 2 - tx >> 2));
					}
				}
				mode = M_IDLE;
				refresh(0);
			} else if (mode == M_TRACE_MV) {
				selTr.place();
				mode = M_IDLE;
			} else if (mode == M_TRACE_SEL) {
				mode = M_TRACE_DRAW;
				Trace t = new Trace(this).pos(mx + 1 >> 1, my + 1 >> 1);
				selTr.connect(t);
				selTrace(t);
			}
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
			blocks.remove(selBlock);
			selBlock = null;
			break;
		case GLFW_KEY_S:
			if (!ctrl) break;
			if (curFile != null && !shift) save(curFile);
			else new FileBrowser(FILE, "Save as ", this::save);
			break;
		case GLFW_KEY_O:
			if (ctrl) new FileBrowser(FILE, "Open ", this::load);
			break;
		case GLFW_KEY_N:
			if (!ctrl) break;
			clear();
			curFile = null;
			setTitle(null);
			break;
		case GLFW_KEY_H:
			if (!ctrl || curFile == null) break;
			extDef.clear();
			try {
				info = loadHeader(curFile)
					? "refreshed external declarations!"
					: "current circuit has no .c file!";
			} catch(IOException e) {
				e.printStackTrace();
				info = e.getMessage();
			}
			break;
		case GLFW_KEY_W:
			if (ctrl) glfwSetWindowShouldClose(WINDOW, true);
			break;
		case GLFW_KEY_D:
			if (ctrl) cleanUpTraces();
			break;
		case GLFW_KEY_T:
			if (ctrl) typeCheck();
			break;
		case GLFW_KEY_M:
			if (!ctrl) break;
			if (curFile != null) compile(curFile, shift);
			else new FileBrowser(FILE, "Compile to ", f -> compile(f, shift));
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
		while(t != null && t.pin > 0) t = t.from;
		info = t == null || t.block == null ? ""
			: t.block.outType.displayString(new StringBuilder(), true).toString();
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
		if (selTr != null)
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
		extDef.clear();
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

	private boolean loadHeader(File file) throws IOException {
		File h = withSuffix(file, ".c");
		if (!h.exists()) return false;
		Profiler t = new Profiler(System.out);
		HeaderParser hp = new HeaderParser();
		hp.processHeader(h, true);
		t.end("header preprocessed");
		hp.getDeclarations(extDef);
		t.end("header parsed");
		return true;
	}

	private void typeCheck() {
		Profiler t = new Profiler(System.out);
		CircuitFile file = new CircuitFile(blocks, extDef);
		t.end("parsed");
		try {
			file.typeCheck();
			info = "Type checked!";
			selBlock = blocks.get(file.out);
		} catch(SignalError e) {
			info = "Error: " + e.getMessage();
			if (e.block >= 0) {
				selBlock = blocks.get(e.block);
				selTr = selBlock.io[e.in + 1];
			} else selBlock = null;
		}
		t.end("checked");
		for (int i = 0; i < blocks.size(); i++) {
			Block block = blocks.get(i);
			Node val = file.nodes[i];
			block.outType = val == null || val.out == null ? Signal.NULL : val.out;
		}
		refresh(0);
		redraw = -1;
	}

	private void compile(File file, boolean debug) {
		file = withSuffix(file, ".ll");
		Profiler t = new Profiler(System.out);
		Instruction code = Instruction.compile();
		t.end("sequentialized");
		Instruction.initIds(code);
		t.end("resolved variables");
		try (FileWriter fw = new FileWriter(file, US_ASCII)){
			Instruction.print(fw, code, debug);
			t.end("written");
			info = "compiled!";
		/*} catch(CompileError e) {
			selBlock = e.idx >= 0 ? blocks.get(e.idx + paletteSize) : null;
			changeInfo("Error: " + e.getMessage());
			return;*/
		} catch(IOException e) {
			e.printStackTrace();
			info = e.toString();
		}
		refresh(0);
	}

	static final File FILE = new File("./test");

	private static File withSuffix(File file, String sfx) {
		String name = file.getName();
		if (name.endsWith(sfx)) return file;
		int i = name.lastIndexOf(sfx.charAt(0));
		name = (i < 0 ? name : name.substring(0, i)).concat(sfx);
		return new File(file.getParent(), name);
	}

	private void load(File file) {
		if (file == null) return;
		setTitle((curFile = file).getName());
		try(ExtInputStream in = new ExtInputStream(new FileInputStream(file))) {
			clear();
			CircuitFile.load(this, in);
			in.close();
			loadHeader(file);
			info = "loaded!";
		} catch(IOException | DataFormatException e) {
			e.printStackTrace();
			info = e.toString();
		}
		refresh(0);
	}

	private void save(File file) {
		if (file == null) return;
		curFile = file = withSuffix(file, ".dfc");
		setTitle(file.getName());
		try {
			file.createNewFile();
			try(ExtOutputStream out = new ExtOutputStream(new FileOutputStream(file))) {
				CircuitFile.save(blocks, out);
			}
			info = "saved!";
		} catch (IOException e) {
			e.printStackTrace();
			info = e.toString();
		}
		refresh(0);
	}

	@Override
	public BlockDef apply(String t) {
		return icons.get(t);
	}

}
