package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.opengl.GL30C.*;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.BlockRegistry;

/**
 * @author CD4017BE */
public class Palette implements IGuiSection {

	private static final String[] DEF_LIST = {
		"in", "out",
		"main", "def", "#x", "call", "swt", "loop", "for",
		"get", "set", "pack", "pre", "post", "struct", "vector", "array",
		"count", "zero", "type", "funt",
		"ref", "load", "store",
		"#uw", "#us", "#ui", "#ul", "#w", "#s", "#i", "#l",
		"#f", "#d", "add", "sub", "mul", "div", "mod", "neg",
		"#b", "eq", "ne", "gt", "ge", "lt", "le",
		"or", "and", "xor", "not"
	};

	private static final int SCALE = 32;
	public final BlockIcons icons;
	public BlockDef[] palette;
	public int idx;
	int bw, bh, nw, scroll;
	int blockBuf, blockVAO;
	CircuitEditor circuit;

	public Palette(BlockIcons icons, BlockRegistry reg) {
		this.icons = icons;
		this.palette = new BlockDef[DEF_LIST.length];
		int i = 0, w = 0, h = 0;
		for (String name : DEF_LIST) {
			BlockDef def = palette[i++] = reg.get(name);
			icons.load(def, reg);
			w = max(w, def.icon.w);
			h = max(h, def.icon.h);
		}
		this.bw = w;
		this.bh = h;
		
		allocSelBuf(2);
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
		
		addSel(0, 0, palette.length * bw, bh, 0x80ffffff);
		if (idx >= 0) addSel(idx * bw, 0, bw, bh, 0xff80ff80);
		drawSel(-ofsX, 1F, scaleX, -scaleY, 0F, 0.5F, 0x80000000);
		
		glUseProgram(blockP);
		glUniformMatrix3x4fv(block_transform, false, new float[] {
			scaleX, 0, 0, 0,
			0, -scaleY, 0, 0,
			-ofsX, 1F, 0, 1F
		});
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
