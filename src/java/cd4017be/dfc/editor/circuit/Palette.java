package cd4017be.dfc.editor.circuit;

import static cd4017be.dfc.editor.Shaders.*;
import static org.lwjgl.glfw.GLFW.*;

import java.util.ArrayList;
import cd4017be.dfc.editor.circuit.BlockList.BlockConsumer;
import cd4017be.dfc.editor.gui.*;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.Module.PaletteGroup;

/**
 * @author CD4017BE */
public class Palette extends GuiGroup {

	public static final int WIDTH = 64;

	private final Label modsL, blocksL, blockName;
	private final BlockList blocks;
	private final int lastIH, lastD;
	private final ArrayList<PaletteGroup> palettes = new ArrayList<>();
	private int msel;

	public Palette(GuiGroup parent, BlockConsumer action) {
		super(parent, 2);
		parent.add(this);
		this.modsL = new Label(this).text("Modules:").color(FG_WHITE);
		this.blocksL = new Label(this).text("Blocks:").color(FG_WHITE);
		this.blockName = new Label(this).color(FG_GREEN_XL);
		this.blocks = new BlockList(this, blockName, action);
		this.lastIH = inputHandlers.size();
		this.lastD = drawables.size();
	}

	public Palette setModule(Module m) {
		if (palettes.size() != 0 && palettes.get(0).module == m) return this;
		palettes.clear();
		palettes.addAll(m.palettes.values());
		for (Module mod : m.imports.values())
			palettes.addAll(mod.ensureLoaded().palettes.values());
		msel = 0;
		chop(lastD, lastIH);
		modsL.pos(0, 0, 12);
		int i = 0, y = 0;
		for (PaletteGroup pg : palettes)
			moduleButton(i++, y += 12, pg.name);
		blocksL.pos(0, y += 12, 12);
		blockName.pos(0, y += 12, 12);
		if (palettes.isEmpty()) {
			blocks.selModule(null);
			blockName.text("No palettes available");
		} else selModule(0);
		blocks.pos(0, y += 12, WIDTH);
		return this;
	}

	private void moduleButton(int i, int y, String name) {
		new Button(this).text(name).pos(0, y, WIDTH, 12).color(FG_GRAY_L).action((b, mb) -> {
			if (mb == GLFW_MOUSE_BUTTON_LEFT && i != msel) selModule(i);
		});
	}

	private void selModule(int i) {
		((Button)inputHandlers.get(lastIH + msel)).color(FG_GRAY_L);
		blocks.selModule(palettes.get(msel = i));
		((Button)inputHandlers.get(lastIH + msel)).color(FG_YELLOW_L);
	}

	@Override
	public void onResize(long window, int w, int h) {
		x0 = 0;
		x1 = WIDTH * scale;
		y0 = 0;
		y1 = h;
	}

}
