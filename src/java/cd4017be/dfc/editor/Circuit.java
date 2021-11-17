package cd4017be.dfc.editor;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.DataFormatException;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import cd4017be.dfc.compiler.Instruction;
import cd4017be.dfc.lang.*;
import cd4017be.util.*;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static cd4017be.dfc.editor.Shaders.*;
import static cd4017be.dfc.lang.IntrinsicEvaluators.INTRINSICS;
import static cd4017be.util.GLUtils.*;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;

/**Implements the circuit editor user interface.
 * @author CD4017BE */
public class Circuit implements IGuiSection {

	/**Editing modes */
	private static final byte M_IDLE = 0, M_BLOCK_SEL = 1, M_TRACE_SEL = 2, M_TRACE_MV = 3, M_TRACE_DRAW = 4;

	public final IndexedSet<Block> blocks;
	public final HashMap<Integer, Trace> traces;
	final BlockIcons icons;
	/** GL vertex arrays */
	final int blockVAO, traceVAO, selVAO, textVAO;
	/** GL buffers */
	final int blockBuf, traceBuf, selBuf, charBuf, indexBuf;
	/** GL textures */
	final int textPos;
	/** draw counters */
	int blockCount, traceCount, selCount, charCount;
	/** editor grid size */
	int width, height;
	/** 0: nothing, 1: frame, 2: texts, -1: everything */
	byte redraw;
	/** current editing mode */
	byte mode;
	int paletteSize;
	/** mouse positions */
	int mx = -1, my = -1, rx, ry;
	Trace selTr;
	Block selBlock;
	final ByteBuffer info;

	public Circuit() {
		this.textVAO = genTextVAO(charBuf = glGenBuffers(), indexBuf = glGenBuffers());
		this.blockVAO = genBlockVAO(blockBuf = glGenBuffers());
		this.traceVAO = genTraceVAO(traceBuf = glGenBuffers());
		this.selVAO = genSelVAO(selBuf = glGenBuffers());
		this.icons = new BlockIcons(INTRINSICS);
		this.textPos = glGenBuffers();
		checkGLErrors();
		this.blocks = new IndexedSet<>(new Block[16]);
		this.traces = new HashMap<>();
		this.info = MemoryUtil.memAlloc(64);
		
		fullRedraw();
		checkGLErrors();
		glUseProgram(traceP);
		glUniform2f(trace_lineSize, 0.25F, 1F);
		checkGLErrors();
		glUseProgram(selP);
		glUniform2f(sel_edgeRange, 0F, 2F);
		checkGLErrors();
		initFont(font_tex, FONT_CW, FONT_CH, FONT_STRIDE);
		glUniform2f(text_gridSize, 0.25F, 1F/6F);
		glBindBuffer(GL_TEXTURE_BUFFER, textPos);
		glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA16I, textPos);
		checkGLErrors();
	}

	@Override
	public void onResize(int w, int h) {
		width = w >> 4;
		height = h >> 4;
		if (paletteSize == 0) {
			setupPalette();
			new Block(BlockDef.OUT_ID, this).pos(width >> 1, 1).place();
		}
	}

	private void setupPalette() {
		int y = 0, x = 0, w = 0;
		for (String s : INTRINSICS.keySet()) {
			Block block = new Block(s, this).pos(x, y);
			w = Math.max(w, block.w());
			y += block.h();
			if (y < height - 4) continue;
			x += w; w = 0; y = 0;
		}
		paletteSize = blocks.size();
	}

	@Override
	public void redraw() {
		if (icons.update() || redraw < 0) redrawBlocks();
		if ((redraw & 2) != 0) redrawText();
		if (redraw != 0) redrawSel();
		redraw = 0;
		//draw frame
		icons.bind();
		float[] mat = new float[] {
			2F/width,   0, 0, 0,
			0, -2F/height, 0, 0,
			-1F,       1F, 0, 1F
		};
		glUseProgram(traceP);
		glUniformMatrix3x4fv(trace_transform, false, mat);
		glBindVertexArray(traceVAO);
		glDrawArrays(GL_LINE_STRIP, 0, traceCount);
		checkGLErrors();
		
		glUseProgram(blockP);
		glUniformMatrix3x4fv(block_transform, false, mat);
		glBindVertexArray(blockVAO);
		glDrawArrays(GL_POINTS, 0, blockCount);
		checkGLErrors();
		
		glUseProgram(selP);
		mat[0] = 0.5F/width; mat[5] = -0.5F/height;
		glUniformMatrix3x4fv(sel_transform, false, mat);
		glBindVertexArray(selVAO);
		glDrawArrays(GL_POINTS, 0, selCount);
		checkGLErrors();
		
		glUseProgram(textP);
		glBindTexture(GL_TEXTURE_2D, font_tex);
		mat[0] = 2F/width; mat[5] = -3F/height;
		mat[9] -= 0.5F/height;
		glUniformMatrix3x4fv(text_transform, false, mat);
		setColor(text_bgColor, 0x00000000);
		setColor(text_fgColor, 0xffffff80);
		glBindVertexArray(textVAO);
		glDrawArrays(GL_POINTS, 0, charCount);
		checkGLErrors();
	}

	private static byte print(
		ByteBuffer pbuf, int p0, ByteBuffer cbuf,
		ByteBuffer ibuf, int xy, int w
	) {
		byte i = (byte)(pbuf.position() >> 3);
		pbuf.putInt(xy).putShort((short)w).putShort((short)p0);
		ibuf.limit(cbuf.position());
		while(ibuf.hasRemaining()) ibuf.put(i);
		return i;
	}

	private void redrawText() {
		int n = 1;
		charCount = info.capacity();
		for (Block block : blocks) {
			int l = block.data.length();
			if (l == 0) continue;
			n++;
			charCount += l;
		}
		try (MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer pbuf = ms.malloc(TEXT_POS_STRIDE * n);
			ByteBuffer cbuf = ms.malloc(charCount), ibuf = ms.malloc(charCount);
			print(pbuf, 0, cbuf.put(info.clear()), ibuf, height - 2 << 18, width);
			for (Block block : blocks) {
				block.textOfs = cbuf.position();
				if (block.data.isEmpty()) continue;
				block.textIdx = print(
					pbuf, block.textOfs, cbuf.put(block.data.getBytes(US_ASCII)),
					ibuf, block.textPos(), block.w() * 2
				);
			}
			glBufferData(GL_TEXTURE_BUFFER, pbuf.flip(), GL_STATIC_DRAW);
			glBindBuffer(GL_ARRAY_BUFFER, charBuf);
			glBufferData(GL_ARRAY_BUFFER, cbuf.flip(), GL_STATIC_DRAW);
			glBindBuffer(GL_ARRAY_BUFFER, indexBuf);
			glBufferData(GL_ARRAY_BUFFER, ibuf.flip(), GL_STATIC_DRAW);
		}
	}

	private void redrawSel() {
		try (MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(SEL_STRIDE * 2);
			if (selBlock != null) {
				buf.putShort((short)(selBlock.x * 4)).putShort((short)(selBlock.y * 4));
				buf.putInt(selBlock.w() << 2 | selBlock.h() << 18).putInt(0xff8080ff);
			}
			if (selTr != null) {
				buf.putShort((short)(selTr.x() * 4 - 2)).putShort((short)(selTr.y() * 4 - 2));
				buf.putInt(0x00040004).putInt(0xffff8080);
			}
			glBindBuffer(GL_ARRAY_BUFFER, selBuf);
			glBufferData(GL_ARRAY_BUFFER, buf.flip(), GL_STREAM_DRAW);
			selCount = buf.limit() / SEL_STRIDE;
		}
		
	}

	private void redrawBlocks() {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			blockCount = blocks.size();
			for (Block block : blocks) blockCount += block.def.textSize >> 15 & 1;
			ByteBuffer buf = ms.malloc(blockCount * BLOCK_STRIDE);
			for (Block block : blocks) {
				block.posOfs = buf.position();
				block.draw(buf);
			}
			glBindBuffer(GL_ARRAY_BUFFER, blockBuf);
			glBufferData(GL_ARRAY_BUFFER, buf.flip(), GL_DYNAMIC_DRAW);
		}
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
		Main.checkGLErrors();
	}

	@Override
	public boolean onMouseMove(double x, double y) {
		int gx = (int)Math.floor(x * width * 2);
		int gy = (int)Math.floor(y * height * 2);
		if (gx != mx || gy != my)
			moveSel(gx, gy);
		return true;
	}

	private void moveSel(int x, int y) {
		mx = x; my = y;
		if (mode == M_IDLE) {
			selTrace(traces.get(Trace.key(x + 1 >> 1, y + 1 >> 1)));
		} else if (mode == M_TRACE_SEL && selTr.pin < 0) {
			selTr.pickup();
			mode = M_TRACE_MV;
		}
		if (mode == M_TRACE_MV || mode == M_TRACE_DRAW)
			selTr.pos(x + 1 >> 1, y + 1 >> 1);
		else if (mode == M_BLOCK_SEL && selBlock != null)
			selBlock.pickup().pos(x + rx >> 1, y + ry >> 1);
	}

	@Override
	public void onMouseButton(int button, int action, int mods) {
		int x = mx >> 1, y = my >> 1;
		switch(action | button << 1) {
		case GLFW_PRESS | GLFW_MOUSE_BUTTON_LEFT<<1:
			if (mode != M_IDLE) break;
			selBlock = null;
			for (int i = paletteSize; i < blocks.size(); i++) {
				Block block = blocks.get(i);
				if (block.isInside(x, y)) {
					selBlock = block;
					rx = (block.x << 1) - mx;
					ry = (block.y << 1) - my;
					break;
				}
			}
			mode = M_BLOCK_SEL;
			Main.refresh(0);
			redraw |= 1;
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_LEFT<<1:
			if (mode == M_BLOCK_SEL) {
				if (selBlock != null) selBlock.place();
				mode = M_IDLE;
			}
			break;
		case GLFW_PRESS | GLFW_MOUSE_BUTTON_RIGHT<<1:
			if (selTr == null) break;
			if (mode == M_TRACE_DRAW) {
				Trace t = selTr.place();
				if (selTrace(t)) mode = M_IDLE;
				else mode = M_TRACE_SEL;
			} else if (mode == M_IDLE && selTr.pin != 0)
				mode = M_TRACE_SEL;
			else if (mode == M_BLOCK_SEL && selBlock != null) {
				selBlock.place();
				if (selBlock.io.length > 1) {
					selTrace(selBlock.io[1]);
					mode = M_TRACE_SEL;
				} else mode = M_IDLE;
			}
			break;
		case GLFW_RELEASE | GLFW_MOUSE_BUTTON_RIGHT<<1:
			if (mode == M_TRACE_MV) {
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
			for (Block block : blocks)
				if (block.isInside(x, y)) {
					addBlock(block.id());
					break;
				}
			break;
		}
	}

	@Override
	public void onKeyInput(int key, int scancode, int action, int mods) {
		if (action == GLFW_RELEASE) return;
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
		switch(key) {
		case GLFW_KEY_DELETE:
			blocks.remove(selBlock);
			break;
		case GLFW_KEY_BACKSPACE:
			if (selBlock == null) break;
			String s = selBlock.data;
			if (s.length() > 0)
				changeText(selBlock, s.substring(0, s.length() - 1));
			break;
		case GLFW_KEY_S:
			if (ctrl) save(FILE);
			break;
		case GLFW_KEY_L:
			if (ctrl) load(FILE);
			break;
		case GLFW_KEY_D:
			if (ctrl) cleanUpTraces();
			break;
		case GLFW_KEY_T:
			if (ctrl) typeCheck();
			break;
		case GLFW_KEY_M:
			if (ctrl) compile(L_FILE, shift);
			break;
		}
	}

	boolean selTrace(Trace t) {
		if (t == selTr) return false;
		selTr = t;
		while(t != null && t.pin > 0) t = t.from;
		changeInfo(t == null || t.block == null ? "" : "Type: " + Signal.name(t.block.outType));
		return true;
	}

	boolean typing() {
		return selBlock != null && mode == M_IDLE && selBlock.def.textSize < 0;
	}

	@Override
	public void onCharInput(int cp) {
		if (!typing()) return;
		int l = selBlock.def.textSize;
		l = (selBlock.def.icon.w - (l & 15) - (l >> 4 & 15)) * 2 + (l >> 12 & 7);
		String s = selBlock.data;
		if (s.length() >= l) return;
		changeText(selBlock, s + (char)cp);
	}

	private void addBlock(String id) {
		BlockDef type = icons.get(id);
		if (typing()) return;
		rx = 1 - (type.ports[0] << 1);
		ry = 1 - (type.ports[1] << 1);
		selBlock = new Block(type, this).pos(mx + rx >> 1, my + ry >> 1);
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
			glBufferSubData(GL_ARRAY_BUFFER, ofs, ms.bytes((byte)x, (byte)y));
			Main.checkGLErrors();
		}
	}

	public void redrawBlock(Block block) {
		if (redraw < 0) return;
		Main.refresh(0);
		redraw |= 1;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			glBindBuffer(GL_ARRAY_BUFFER, blockBuf);
			ByteBuffer buf = ms.malloc(Shaders.BLOCK_STRIDE * (1 + (block.def.textSize >> 15 & 1)));
			block.draw(buf);
			glBufferSubData(GL_ARRAY_BUFFER, block.posOfs, buf.flip());
			Main.checkGLErrors();
		}
	}

	/**Modify coordinates of a text segment in its vertex buffer.
	 * @param idx text segment index
	 * @param xy packed coordinates */
	public void updateTextPos(byte idx, int xy) {
		if ((redraw & 2) != 0) return;
		Main.refresh(0);
		redraw |= 1;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			glBufferSubData(GL_TEXTURE_BUFFER,
				(idx & 0xff) * Shaders.TEXT_POS_STRIDE,
				ms.ints(xy)
			);
		}
	}

	/**Modify the text drawn on a block.
	 * @param block
	 * @param s new text */
	public void changeText(Block block, String s) {
		Main.refresh(0);
		if (block.data.length() != (block.data = s).length()) {
			redrawBlock(block);
			redraw |= 2;
		}
		if ((redraw & 2) != 0 || s.length() == 0) return;
		redraw |= 1;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			glBindBuffer(GL_ARRAY_BUFFER, charBuf);
			Main.checkGLErrors();
			glBufferSubData(GL_ARRAY_BUFFER, block.textOfs, ms.ASCII(s, false));
			Main.checkGLErrors();
		}
	}

	public void changeInfo(String s) {
		byte[] arr = s.getBytes(US_ASCII);
		info.clear().put(arr, 0, Math.min(arr.length, info.capacity()));
		while(info.hasRemaining()) info.put((byte)0);
		if ((redraw & 2) != 0) return;
		Main.refresh(0);
		redraw |= 1;
		glBindBuffer(GL_ARRAY_BUFFER, charBuf);
		Main.checkGLErrors();
		glBufferSubData(GL_ARRAY_BUFFER, 0, info.flip());
		Main.checkGLErrors();
	}

	/**Schedule a complete re-render of all vertex buffers. */
	public void fullRedraw() {
		Main.refresh(0);
		redraw = -1;
	}

	@Override
	public void close() {
		MemoryUtil.memFree(info);
		glDeleteVertexArrays(textVAO);
		glDeleteVertexArrays(blockVAO);
		glDeleteVertexArrays(traceVAO);
		glDeleteVertexArrays(selVAO);
		glDeleteBuffers(blockBuf);
		glDeleteBuffers(traceBuf);
		glDeleteBuffers(selBuf);
		glDeleteBuffers(charBuf);
		glDeleteBuffers(indexBuf);
		glDeleteBuffers(textPos);
	}

	private void clear() {
		blocks.clear();
		traces.clear();
		setupPalette();
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
		changeInfo("removed " + n + " traces!");
	}

	private void typeCheck() {
		Profiler t = new Profiler(System.out);
		CircuitFile file = new CircuitFile(blocks, paletteSize);
		t.end("parsed");
		try {
			file.typeCheck();
			changeInfo("Type checked!");
			selBlock = blocks.get(file.out + paletteSize);
		} catch(SignalError e) {
			changeInfo("Error: " + e.getMessage());
			if (e.block >= 0) {
				selBlock = blocks.get(e.block + paletteSize);
				selTr = selBlock.io[e.in + 1];
			} else selBlock = null;
		}
		t.end("checked");
		for (int i = paletteSize; i < blocks.size(); i++) {
			Block block = blocks.get(i);
			Node val = file.nodes[i - paletteSize];
			block.outType = val == null || val.out == null ? Signal.DEAD_CODE : val.out;
		}
		Main.refresh(0);
		redraw = -1;
	}

	private void compile(File file, boolean debug) {
		Profiler t = new Profiler(System.out);
		Instruction code = Instruction.compile();
		t.end("sequentialized");
		Instruction.initIds(code);
		t.end("resolved variables");
		try (FileWriter fw = new FileWriter(file, US_ASCII)){
			Instruction.print(fw, code, debug);
			t.end("written");
			changeInfo("compiled!");
		/*} catch(CompileError e) {
			selBlock = e.idx >= 0 ? blocks.get(e.idx + paletteSize) : null;
			changeInfo("Error: " + e.getMessage());
			return;*/
		} catch(IOException e) {
			e.printStackTrace();
			changeInfo(e.toString());
		}
	}

	static final File
	FILE = new File("./test/test.dfc"),
	C_FILE = new File("./test/test.class"),
	L_FILE = new File("./test/test.ll");

	private void load(File file) {
		try(ExtInputStream in = new ExtInputStream(new FileInputStream(file))) {
			clear();
			CircuitFile.load(this, in);
			changeInfo("loaded!");
		} catch(IOException | DataFormatException e) {
			e.printStackTrace();
			changeInfo(e.toString());
		}
	}

	private void save(File file) {
		try {
			file.getParentFile().mkdirs();
			file.createNewFile();
			try(ExtOutputStream out = new ExtOutputStream(new FileOutputStream(file))) {
				CircuitFile.save(blocks, paletteSize, out);
			}
			changeInfo("saved!");
		} catch (IOException e) {
			e.printStackTrace();
			changeInfo(e.toString());
		}
	}

}
