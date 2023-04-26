package cd4017be.dfc.editor.gui;


/**
 * 
 * @author CD4017BE */
public class HoverRectangle implements InputHandler {

	public int x0, y0, x1, y1;

	public HoverRectangle() {}

	public HoverRectangle(int x, int y, int w, int h) {
		this.x0 = x;
		this.y0 = y;
		this.x1 = x + w;
		this.y1 = y + h;
	}

	@Override
	public boolean onMouseMove(int mx, int my) {
		return mx >= x0 && my >= y0 && mx < x1 && my < y1;
	}

}
