package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static cd4017be.dfc.lang.IntrinsicEvaluators.INTRINSICS;
import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.opengl.GL30C.*;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.lang.BlockDef;

/**
 * @author CD4017BE */
public class Palette implements IGuiSection {

	private static final String[] DEF_LIST = {
		"main", "def", "#X", "call", "swt", "loop",
		"pack", "pick", "void", "count", "zero",
		"type", "#T", "ptrt", "funt", "elt0", "elt1",
		"ref", "idx", "load", "store",
		"#N", "add", "sub", "mul", "div", "mod", "udiv", "umod", "neg",
		"eq", "ne", "gt", "ge", "ugt", "uge",
		"or", "and", "xor", "not"
	};

	private static final int SCALE = 32;
	public final BlockIcons icons;
	public BlockDef[] palette;
	public int idx;
	int bw, bh, nw, scroll;
	int blockBuf, blockVAO, selBuf, selVAO;
	Circuit circuit;

	public Palette() {
		this.icons = new BlockIcons(INTRINSICS);
		this.palette = new BlockDef[DEF_LIST.length];
		int i = 0, w = 0, h = 0;
		for (String name : DEF_LIST) {
			BlockDef def = palette[i++] = icons.get(name);
			w = max(w, def.icon.w);
			h = max(h, def.icon.h);
		}
		this.bw = w;
		this.bh = h;
		
		selVAO = genSelVAO(selBuf = glGenBuffers());
		glBufferData(GL_ARRAY_BUFFER, 2 * SEL_STRIDE, GL_STATIC_DRAW);
		blockVAO = genBlockVAO(blockBuf = glGenBuffers());
		try(MemoryStack ms = MemoryStack.stackPush()) {
			int x = 0, y = 0, l = palette.length;
			ByteBuffer buf = ms.malloc(l * BLOCK_STRIDE);
			for (BlockDef def : palette) {
				buf.putShort((short)(x + (bw - def.icon.w >> 1)))
				.putShort((short)(y + (bh - def.icon.h >> 1)))
				.putShort((short)0).putShort((short)def.icon.id);
				x += bw;
			}
			glBufferData(GL_ARRAY_BUFFER, buf.flip(), GL_STATIC_DRAW);
			buf = ms.malloc(SEL_STRIDE);
			buf.putShort((short)0).putShort((short)0)
			.putShort((short)(l * bw * 4)).putShort((short)(bh * 4))
			.putInt(0x80ffffff);
			glBindBuffer(GL_ARRAY_BUFFER, selBuf);
			glBufferSubData(GL_ARRAY_BUFFER, 0, buf.flip());
		}
		idx = -1;
	}

	@Override
	public void onResize(int w, int h) {
		nw = w / (SCALE * bw);
		scroll = min(palette.length - nw, max(scroll, nw));
	}

	@Override
	public void redraw() {
		float scaleX = SCALE / (float)WIDTH;
		float scaleY = SCALE / (float)HEIGHT;
		float ofsX = scaleX * bw * scroll;
		float[] mat = new float[] {
			0.25F * scaleX, 0, 0, 0,
			0, -0.25F * scaleY, 0, 0,
			-ofsX, 1F, 0, 1F
		};
		glUseProgram(selP);
		glUniformMatrix3x4fv(sel_transform, false, mat);
		glUniform4f(sel_bgColor, 0, 0, 0, 0.5F);
		glBindVertexArray(selVAO);
		glDrawArrays(GL_POINTS, 0, idx < 0 ? 1 : 2);
		glUniform4f(sel_bgColor, 0, 0, 0, 0);
		checkGLErrors();
		
		glUseProgram(blockP);
		mat[0] = scaleX; mat[5] = -scaleY;
		glUniformMatrix3x4fv(block_transform, false, mat);
		icons.bind();
		glBindVertexArray(blockVAO);
		glDrawArrays(GL_POINTS, 0, palette.length);
		checkGLErrors();
		
		glBindVertexArray(0);
	}

	@Override
	public boolean onMouseMove(double mx, double my) {
		if ((my + 1.0) * (double)HEIGHT > (double)(bh * SCALE)) {
			if (idx != (idx = -1)) refresh(0);
			return false;
		}
		int i = (int)floor(mx * (double)WIDTH / (double)(bw * SCALE)) + scroll;
		if (i < 0 || i >= palette.length) i = -1;
		else try (MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(SEL_STRIDE);
			buf.putShort((short)(i * bw * 4)).putShort((short)0)
			.putShort((short)(bw * 4)).putShort((short)(bh * 4)).putInt(0xff80ff80);
			glBindBuffer(GL_ARRAY_BUFFER, selBuf);
			glBufferSubData(GL_ARRAY_BUFFER, SEL_STRIDE, buf.flip());
		}
		if (idx != (idx = i)) refresh(0);
		return true;
	}

	@Override
	public void onMouseButton(int button, int action, int mods) {
		if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_RELEASE && idx >= 0)
			circuit.addBlock(palette[idx]);
	}

	@Override
	public void onScroll(double dx, double dy) {
		int d = nw + 1 >> 1;
		if (dy < 0) scroll = min(scroll + d, palette.length - nw);
		else if (dy > 0) scroll = max(scroll - d, nw);
		else return;
		refresh(0);
	}

}
