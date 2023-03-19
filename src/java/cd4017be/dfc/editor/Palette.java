package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static cd4017be.dfc.lang.LoadingCache.ATLAS;
import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map.Entry;

import org.lwjgl.system.MemoryStack;
import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.Module.PaletteGroup;
import cd4017be.util.*;

/**
 * @author CD4017BE */
public class Palette implements IGuiSection {

	public String[] modNames;
	public Module[] modules;
	public PaletteGroup palette;
	int[] positions, indices, widths;
	int msel, gsel, mhvr = -1, bhvr = -1, brow = -1;
	int scroll;
	int blockBuf;
	final VertexArray blockVAO;
	final CircuitEditor circuit;

	public Palette(CircuitEditor circuit) {
		this.circuit = circuit;
		this.blockVAO = genBlockVAO(16);
	}

	public Palette setModule(Module m) {
		if (modules != null && modules[0] == m) return this;
		this.modules = new Module[m.imports.size() + 1];
		this.modNames = new String[modules.length];
		modNames[0] = "---";
		modules[0] = m;
		int i = 0;
		for (Entry<String, Module> e : m.imports.entrySet()) {
			modNames[++i] = e.getKey();
			modules[i] = e.getValue();
		}
		selModule(0);
		return this;
	}

	private void selModule(int i) {
		msel = i;
		selGroup(0);
	}

	private void selGroup(int i) {
		Module m = modules[msel].ensureLoaded();
		gsel = i;
		palette = i < m.groups.size() ? m.groups.get(i) : new PaletteGroup("");
		var blocks = palette.blocks();
		final int rw = 16;
		int nr = 1, nc = 0, w = 0;
		int l = blocks.size();
		//pass 1: count rows and init icons
		for (BlockDef def : blocks) {
			def.model.loadIcon();
			AtlasSprite icon = def.model.icon;
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
			AtlasSprite icon = blocks.get(j).model.icon;
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
		//pass 3: render
		blockVAO.clear();
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(l * BLOCK_PRIMLEN);
			for (int ir = 0, j = 0; ir < nr; ir++) {
				y = positions[ir];
				w = widths[ir];
				h = positions[ir + 1] - y;
				for (int x = 0, i1 = indices[ir + 1]; j < i1; j++, x += w) {
					AtlasSprite icon = blocks.get(j).model.icon;
					drawBlock(buf,
						x + (w - icon.w >> 1),
						y + (h - icon.h >> 1),
						icon.w, icon.h, icon
					);
				}
			}
			blockVAO.append(buf.flip());
		}
		bhvr = -1;
		refresh(0);
	}

	@Override
	public void onResize(int w, int h) {
	}

	@Override
	public void redraw() {
		float scaleX = 8F / (float)WIDTH;
		float scaleY = -8F / (float)HEIGHT;
		int l = modNames.length;
		var blocks = palette.blocks();
		addSel(0, 0, 32, l * 5 + 15 + positions[positions.length - 1] * 2, BG_BLACK | FG_GRAY_D);
		print("Module:", FG_WHITE, 1, 1, 2, 3);
		for (int i = 0; i < l; i++) {
			String name = modNames[i];
			print(name, FG_WHITE, 16 - name.length(), i * 5 + 6, 2, 3);
			addSel(0, i * 5 + 5, 32, 5, i == msel ? FG_YELLOW_L : i == mhvr ? FG_GRAY_L : FG_GRAY_D);
		}
		print("Blocks: " + palette.name(), FG_WHITE, 1, l * 5 + 6, 2, 3);
		if (bhvr >= 0) {
			int w = widths[brow], y0 = positions[brow], y1 = positions[brow + 1];
			addSel((bhvr - indices[brow]) * w * 2, y0 * 2 + l * 5 + 15, w * 2, (y1 - y0) * 2, FG_GREEN_L);
			print(blocks.get(bhvr).name, FG_GREEN_XL, 1, l * 5 + 10, 2, 3);
		}
		drawSel(-1F, 1F, scaleX, scaleY, 0F, 1F);
		drawText(-1F, 1F, scaleX, scaleY);
		ATLAS.bind();
		transform(block_transform, -1F, 1F + scaleY * (l + 3) * 5F, 2F * scaleX, 2F * scaleY);
		blockVAO.draw();
	}

	@Override
	public boolean onMouseMove(double mx, double my) {
		mx = (mx + 1.0) * (double)WIDTH / 8.0;
		my = (my + 1.0) * (double)HEIGHT / 8.0;
		boolean inrange = mx < 32.0;
		int mhvr = -1, bhvr = -1, l = modNames.length;
		if (inrange && my >= 5.0 && my < (l + 1) * 5.0)
			mhvr = (int)floor(my / 5.0 - 1.0);
		if (inrange && my >= (l + 2) * 5.0) {
			int y = (int)floor((my - (l + 3) * 5.0) * 0.5);
			int brow = Arrays.binarySearch(positions, y);
			if (brow < 0) brow = ~brow - 1;
			this.brow = brow;
			if (brow >= 0 && brow < positions.length - 1) {
				bhvr = indices[brow] + (int)floor(mx / (double)(widths[brow]*2));
				if (bhvr >= indices[brow + 1]) bhvr = -1;
			}
		}
		if (
			this.mhvr != (this.mhvr = mhvr) |
			this.bhvr != (this.bhvr = bhvr)
		) refresh(0);
		if (inrange) glfwSetCursor(WINDOW, MAIN_CURSOR);
		return inrange;
	}

	@Override
	public void onMouseButton(int button, int action, int mods) {
		if (action != GLFW_RELEASE) return;
		if (button == GLFW_MOUSE_BUTTON_LEFT) {
			if (mhvr >= 0 && mhvr != msel) selModule(mhvr);
			if (bhvr >= 0) circuit.addBlock(palette.blocks().get(bhvr));
		} else if (button == GLFW_MOUSE_BUTTON_RIGHT && bhvr >= 0) {
			circuit.open(palette.blocks().get(bhvr));
		}
	}

	@Override
	public void onScroll(double dx, double dy) {
		int d = Double.compare(0, dy);
		if (d == 0) return;
		int l = modules[msel].groups.size();
		if (l > 1) selGroup((gsel + d + l) % l);
	}

	@Override
	public void close() {
		
	}

}
