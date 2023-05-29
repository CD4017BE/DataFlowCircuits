package cd4017be.dfc.editor.circuit;

import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static org.lwjgl.glfw.GLFW.*;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.editor.gui.*;
import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.Module.PaletteGroup;
import cd4017be.util.AtlasSprite;


/**
 * 
 * @author CD4017BE */
public class BlockList extends HoverRectangle implements Drawable {

	public final Palette gui;
	final BlockConsumer action;
	final Label blockName;
	BlockDef[] blocks = new BlockDef[0];
	int[] positions = {0}, indices = {0}, widths = new int[0];
	int bhvr = -1, brow = -1;

	public BlockList(Palette gui, Label blockName, BlockConsumer action) {
		this.gui = gui;
		this.blockName = blockName;
		this.action = action;
		gui.drawables.add(this);
		gui.inputHandlers.add(this);
	}

	@Override
	public void redraw() {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(blocks.length * BLOCK_PRIMLEN);
			for (int ir = 0, j = 0; ir < widths.length; ir++) {
				int y = positions[ir];
				int w = widths[ir];
				int h = positions[ir + 1] - y;
				y += y0 >> 2;
				for (int x = x0 >> 2, i1 = indices[ir + 1]; j < i1; j++, x += w) {
					AtlasSprite icon = blocks[j].model.icon;
					drawBlock(buf,
						x + (w - icon.w >> 1),
						y + (h - icon.h >> 1),
						icon.w, icon.h, icon
					);
				}
			}
			gui.sprites.append(buf.flip());
		}
		if (bhvr >= 0) {
			int w = widths[brow], p0 = positions[brow], p1 = positions[brow + 1];
			addSel((x0 >> 1) + (bhvr - indices[brow]) * w * 2, (y0 >> 1) + p0 * 2, w * 2, (p1 - p0) * 2, FG_GREEN_L);
		}
	}

	@Override
	public boolean onMouseMove(int mx, int my) {
		if (!super.onMouseMove(mx, my)) return false;
		int y = my - y0 >> 2;
		int brow = Arrays.binarySearch(positions, y), bhvr = -1;
		if (brow < 0) brow = ~brow - 1;
		this.brow = brow;
		if (brow >= 0 && brow < positions.length - 1) {
			bhvr = indices[brow] + ((mx - x0 >> 2) / widths[brow]);
			if (bhvr >= indices[brow + 1]) bhvr = -1;
		}
		selBlock(bhvr);
		return true;
	}

	@Override
	public void updateHover() {
		if (gui.hovered() != this) selBlock(-1);
	}

	private void selBlock(int bhvr) {
		if (this.bhvr == (this.bhvr = bhvr)) return;
		blockName.text(bhvr >= 0 ? blocks[bhvr].name : "");
	}

	@Override
	public boolean onMouseButton(int button, int action, int mods) {
		if (bhvr < 0) return false;
		if (action != GLFW_RELEASE) gui.focus(this);
		else this.action.useBlock(blocks[bhvr], button);
		return true;
	}

	public void pos(int x, int y, int w) {
		this.x0 = x;
		this.y0 = y;
		this.x1 = x + w;
		this.y1 = y + positions[positions.length - 1] * 4;
	}

	public void selModule(PaletteGroup pal) {
		int l = pal == null ? 0 : pal.blocks.length;
		blocks = new BlockDef[l];
		final int rw = Palette.WIDTH >> 2;
		int nr = 1, nc = 0, w = 0;
		//pass 1: count rows and init icons
		for (int i = 0; i < l; i++) {
			BlockDef def = pal.blocks[i];
			blocks[i] = def;
			AtlasSprite icon = def.loadModel().icon;
			if ((++nc) * (w = max(w, icon.w)) > rw) {
				nr++;
				nc = 1;
				w = icon.w;
			}
		}
		positions = new int[nr + 1];
		indices = new int[nr + 1];
		widths = new int[nr];
		//pass 2: calculate layout
		positions[0] = 0;
		indices[0] = 0;
		nr = 0; nc = 0; w = 0;
		int h = 0, y = 0;
		for (int j = 0; j < l; j++) {
			AtlasSprite icon = blocks[j].model.icon;
			int w1 = max(w, icon.w);
			if (++nc * w1 > rw) {
				widths[nr++] = w;
				indices[nr] = j;
				positions[nr] = y += h;
				nc = 1; h = 0;
				w = icon.w;
			} else w = w1;
			h = max(h, icon.h);
		}
		widths[nr++] = w;
		indices[nr] = l;
		positions[nr] = y += h;
		y1 = y0 + y * 4;
		selBlock(-1);
		gui.markDirty();
	}

	public interface BlockConsumer {
		void useBlock(BlockDef def, int mb);
	}

}
