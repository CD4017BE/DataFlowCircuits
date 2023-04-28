package cd4017be.dfc.editor.gui;

import static cd4017be.dfc.editor.Shaders.*;
import org.lwjgl.glfw.GLFW;
import cd4017be.dfc.graphics.SpriteModel;

/**
 * @author CD4017BE */
public class Button extends HoverRectangle implements Drawable {

	public final GuiGroup gui;
	public ButtonAction action = NOP;
	public SpriteModel normalModel, pressedModel;
	public String text;
	public int color = FG_GRAY_L;

	public Button(GuiGroup gui) {
		this.gui = gui;
		gui.drawables.add(this);
		gui.inputHandlers.add(this);
	}

	public Button action(ButtonAction action) {
		this.action = action;
		return this;
	}

	public Button pos(int x, int y, int w, int h) {
		x0 = x * 4;
		x1 = (x + w) * 4;
		y0 = y * 4;
		y1 = (y + h) * 4;
		gui.markDirty();
		return this;
	}

	public Button text(String text) {
		this.text = text;
		gui.markDirty();
		return this;
	}

	public Button model(SpriteModel normal, SpriteModel press) {
		this.normalModel = normal;
		this.pressedModel = press;
		gui.markDirty();
		return this;
	}

	public Button color(int color) {
		this.color = color;
		gui.markDirty();
		return this;
	}

	@Override
	public void redraw() {
		SpriteModel model = gui.focused() == this ? pressedModel : normalModel;
		int w = x1 - x0, h = y1 - y0;
		if (model != null) {
			drawBlock(gui.sprites, x0 >> 2, y0 >> 2, w >> 2, h >> 2, model.icon);
			if (text != null)
				print(text, color, (x0 + x1 + model.tx0 + model.tx1 >> 1) - (model.icon.w + text.length()) * 2, y0 + model.ty0 + 1, 4, 6);
		}
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
			this.action.handle(this, button);
		}
		gui.markDirty();
		return true;
	}

	public static final ButtonAction NOP = (b, mb) -> {};

	@FunctionalInterface
	public interface ButtonAction {
		void handle(Button b, int mb);
	}

}