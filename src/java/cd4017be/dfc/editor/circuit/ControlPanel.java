package cd4017be.dfc.editor.circuit;

import static cd4017be.dfc.editor.Shaders.FG_YELLOW_L;

import cd4017be.dfc.editor.gui.GuiGroup;
import cd4017be.dfc.editor.gui.Label;


/**
 * 
 * @author CD4017BE */
public class ControlPanel extends GuiGroup {

	public static final int HEIGHT = 8;

	final Label info;

	public ControlPanel(GuiGroup parent) {
		super(parent, 2);
		parent.add(this);
		this.info = new Label(this).color(FG_YELLOW_L).pos(0, 0, 8);
	}

	@Override
	public void onResize(long window, int w, int h) {
		x0 = Palette.WIDTH * scale;
		x1 = w;
		y0 = h - HEIGHT * scale;
		y1 = h;
	}

	public void statusMsg(String msg) {
		info.text(msg);
	}

}
