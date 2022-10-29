package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.opengl.GL20C.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.BlockRegistry;
import cd4017be.util.VertexArray;

/**
 * @author CD4017BE */
public class Palette implements IGuiSection {

	private static final int SCALE = 32;
	public final BlockIcons icons;
	public BlockDef[] palette;
	public int idx;
	int bw, bh, nw, scroll;
	int blockBuf;
	VertexArray blockVAO;
	CircuitEditor circuit;

	public Palette(BlockIcons icons, BlockRegistry reg) {
		this.icons = icons;
		this.palette = Arrays.stream(reg.sourcePaths)
		.flatMap(t -> {
			try {
				return Files.list(t);
			} catch(IOException e) {
				e.printStackTrace();
				return Stream.empty();
			}
		}).filter(path -> Files.isRegularFile(path))
		.map(path -> path.getFileName().toString())
		.filter(name -> name.endsWith(".dfc"))
		.map(name -> {
			BlockDef def = reg.get(name.substring(0, name.length() - ".dfc".length()));
			if (def != null) icons.load(def, reg);
			return def;
		}).filter(def -> def != null && def.icon != icons.placeholder.icon)
		.collect(ArrayList::new, ArrayList::add, ArrayList::addAll)
		.toArray(BlockDef[]::new);

		for (BlockDef def : palette) {
			bw = max(bw, def.icon.w);
			bh = max(bh, def.icon.h);
		}
		int x = 0, y = 0, l = palette.length;
		blockVAO = genBlockVAO(l);
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(l * BLOCK_STRIDE * 4);
			for (BlockDef def : palette) {
				drawBlock(buf,
					x + (bw - def.icon.w >> 1),
					y + (bh - def.icon.h >> 1),
					def.icon.w, def.icon.h, 0, 0, def.icon.id
				);
				x += bw;
			}
			blockVAO.append(buf.flip());
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
		
		addSel(0, 0, palette.length * bw, bh, FG_WHITE_T | BG_BLACK_T);
		if (idx >= 0) addSel(idx * bw, 0, bw, bh, FG_GREEN_L);
		drawSel(-ofsX, 1F, scaleX, -scaleY, 0F, 0.5F);
		
		glUseProgram(blockP);
		glUniformMatrix3fv(block_transform, false, new float[] {
			scaleX, 0, 0,
			0, -scaleY, 0,
			-ofsX, 1F, 0,
		});
		icons.bind();
		blockVAO.draw();
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
