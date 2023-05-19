package cd4017be.dfc.editor.gui;

import static cd4017be.dfc.editor.Shaders.FG_GRAY_L;
import static cd4017be.dfc.editor.Shaders.print;

/**
 * 
 * @author CD4017BE */
public class Label implements Drawable {

	public final GuiGroup gui;
	public int x, y, w, color = FG_GRAY_L;
	public String text;

	public Label(GuiGroup gui) {
		this.gui = gui;
		gui.drawables.add(this);
	}

	public Label pos(int x, int y, int h) {
		this.x = x;
		this.y = y;
		this.w = h >> 2;
		gui.markDirty();
		return this;
	}

	public Label text(String text) {
		this.text = text;
		gui.markDirty();
		return this;
	}

	public Label color(int color) {
		this.color = color;
		gui.markDirty();
		return this;
	}

	@Override
	public void redraw() {
		if (text == null) return;
		int tw = w & -2;
		print(text, color, x >> 1, y + (w >> 1) + (w & 1) >> 1, tw, tw * 3 >> 1);
	}

}
