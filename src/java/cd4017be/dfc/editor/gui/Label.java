package cd4017be.dfc.editor.gui;

import static cd4017be.dfc.editor.Shaders.print;

/**
 * 
 * @author CD4017BE */
public class Label implements Drawable {

	public int x, y, w, color;
	public String text;

	public Label(String text, int x, int y, int h, int color) {
		this.text = text;
		this.x = x * 4;
		this.y = y * 4;
		this.w = h * 2;
		this.color = color;
	}

	@Override
	public void redraw() {
		print(text, color, x, y + (w >> 2), w, w * 3 >> 1);
	}

}
