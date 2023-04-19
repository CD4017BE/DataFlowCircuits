package cd4017be.dfc.editor.gui;

import static cd4017be.dfc.editor.Shaders.*;

import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import cd4017be.dfc.graphics.SpriteModel;

/**
 * @author CD4017BE */
public class Button extends HoverRectangle implements Drawable {

	public final GuiGroup gui;
	public IntConsumer action;
	public SpriteModel model;
	public String text;
	public int color;

	public Button(
		GuiGroup gui, int x, int y, int w, int h,
		SpriteModel model, String text, int color,
		IntConsumer action
	) {
		super(x, y, w, h);
		this.gui = gui;
		this.model = model;
		this.text = text;
		this.color = color;
		this.action = action;
	}

	@Override
	public void redraw() {
		int w = x1 - x0, h = y1 - y0;
		drawBlock(gui.sprites, x0, y0, w, h, model.icon);
		if (text != null)
			print(text, color, x0 + x1 + model.tx() - text.length(), y0 * 2 + model.ty(), 2, 3);
		if (gui.hovered() == this)
			addSel(x0, y0, w, h, color);
	}

	@Override
	public void updateHover() {
		gui.markDirty();
	}

	@Override
	public boolean onMouseButton(int button, int action, int mods) {
		if (action == GLFW.GLFW_PRESS)
			this.action.accept(button);
		return true;
	}

}
