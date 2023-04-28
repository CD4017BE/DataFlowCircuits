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
	public SpriteModel normalModel, pressedModel;
	public String text;
	public int color;

	public Button(
		GuiGroup gui, String text, int x, int y, int w, int h,
		SpriteModel model, SpriteModel press, int color, IntConsumer action
	) {
		super(x * 4, y * 4, w * 4, h * 4);
		this.gui = gui;
		this.normalModel = model;
		this.pressedModel = press;
		this.text = text;
		this.color = color;
		this.action = action;
	}

	@Override
	public void redraw() {
		SpriteModel model = gui.focused() == this ? pressedModel : normalModel;
		int w = x1 - x0, h = y1 - y0;
		drawBlock(gui.sprites, x0 >> 2, y0 >> 2, w >> 2, h >> 2, model.icon);
		if (text != null)
			print(text, color, (x0 + x1 + model.tx0 + model.tx1 >> 1) - (model.icon.w + text.length()) * 2, y0 + model.ty0 + 1, 4, 6);
		if (gui.hovered() == this)
			addSel(x0, y0, w, h, color);
	}

	@Override
	public void updateHover() {
		gui.markDirty();
	}

	@Override
	public boolean onMouseButton(int button, int action, int mods) {
		if (action == GLFW.GLFW_PRESS) {
			gui.focus(this);
		} else {
			gui.focus(null);
			this.action.accept(button);
		}
		gui.markDirty();
		return true;
	}

}
