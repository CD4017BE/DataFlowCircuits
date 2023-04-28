package cd4017be.dfc.editor.gui;


/**
 * 
 * @author CD4017BE */
public class HoverRectangle implements InputHandler {

	public int x0, y0, x1, y1;

	@Override
	public boolean onMouseMove(int mx, int my) {
		return mx >= x0 && my >= y0 && mx < x1 && my < y1;
	}

}
