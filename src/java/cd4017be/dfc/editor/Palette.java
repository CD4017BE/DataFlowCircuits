package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.*;

import java.nio.ByteBuffer;
import java.util.Map.Entry;

import org.lwjgl.system.MemoryStack;

import cd4017be.compiler.BlockDef;
import cd4017be.compiler.Module;
import cd4017be.util.*;

/**
 * @author CD4017BE */
public class Palette implements IGuiSection {

	public String[] modNames;
	public Module[] modules;
	public BlockDef[] palette;
	int msel, mhvr = -1, bhvr = -1;
	int bw = 1, bh = 1, nw, nh, scroll;
	int blockBuf;
	final VertexArray blockVAO;
	final CircuitEditor circuit;

	public Palette(CircuitEditor circuit) {
		this.circuit = circuit;
		this.blockVAO = genBlockVAO(16);
	}

	public Palette setModule(Module m) {
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
		Module m = modules[i].ensureLoaded();
		this.palette = new BlockDef[m.blocks.size()];
		int j = 0;
		for (BlockDef def : m.blocks.values())
			palette[j++] = def;
		bw = bh = 1;
		for (BlockDef def : palette) {
			def.model.loadIcon();
			AtlasSprite icon = def.model.icon;
			bw = max(bw, icon.w);
			bh = max(bh, icon.h);
		}
		blockVAO.clear();
		nw = 16;
		int x = 0, y = 0, l = palette.length;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(l * BLOCK_PRIMLEN);
			for (BlockDef def : palette) {
				AtlasSprite icon = def.model.icon;
				drawBlock(buf,
					x + (bw - icon.w >> 1),
					y + (bh - icon.h >> 1),
					icon.w, icon.h, icon
				);
				if ((x += bw) > nw - bw) {
					x = 0;
					y += bh;
				}
			}
			blockVAO.append(buf.flip());
		}
		nw /= bw;
		nh = y;
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
		addSel(0, 0, 32, l * 5 + 15 + ((palette.length - 1) / nw + 1) * bh * 2, BG_BLACK | FG_GRAY_D);
		print("Module:", FG_WHITE, 1, 1, 2, 3);
		for (int i = 0; i < l; i++) {
			String name = modNames[i];
			print(name, FG_WHITE, 16 - name.length(), i * 5 + 6, 2, 3);
			addSel(0, i * 5 + 5, 32, 5, i == msel ? FG_YELLOW_L : i == mhvr ? FG_GRAY_L : FG_GRAY_D);
		}
		print("Block:", FG_WHITE, 1, l * 5 + 6, 2, 3);
		if (bhvr >= 0) {
			addSel((bhvr % nw) * bw * 2, (bhvr / nw) * bh * 2 + l * 5 + 15, bw * 2, bh * 2, FG_GREEN_L);
			print(palette[bhvr].name, FG_GREEN_XL, 1, l * 5 + 10, 2, 3);
		}
		drawSel(-1F, 1F, scaleX, scaleY, 0F, 1F);
		drawText(-1F, 1F, scaleX, scaleY);
		circuit.icons.bind();
		transform(block_transform, -1F, 1F + scaleY * (modNames.length + 3) * 5F, 2F * scaleX, 2F * scaleY);
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
			bhvr = (int)floor((my - (l + 3) * 5.0) / (double)(bh*2)) * nw
			     + (int)floor(mx / (double)(bw*2));
			if (bhvr >= palette.length) bhvr = -1;
		}
		if (
			this.mhvr != (this.mhvr = mhvr) |
			this.bhvr != (this.bhvr = bhvr)
		) refresh(0);
		return inrange;
	}

	@Override
	public void onMouseButton(int button, int action, int mods) {
		if (action != GLFW_RELEASE) return;
		if (button == GLFW_MOUSE_BUTTON_LEFT) {
			if (mhvr >= 0 && mhvr != msel) selModule(mhvr);
			if (bhvr >= 0) circuit.addBlock(palette[bhvr]);
		} else if (button == GLFW_MOUSE_BUTTON_RIGHT && bhvr >= 0) {
			circuit.open(palette[bhvr]);
		}
	}

	@Override
	public void onScroll(double dx, double dy) {
	}

	@Override
	public void close() {
		
	}

}
