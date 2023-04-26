package cd4017be.dfc.editor.gui;

import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.lwjgl.glfw.GLFW.*;

import java.util.function.*;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.graphics.SpriteModel;

/**
 * 
 * @author CD4017BE */
public class TextField extends HoverRectangle implements Drawable, InputHandler {

	public final GuiGroup gui;
	public SpriteModel model;
	public Supplier<String> text;
	public Consumer<String> modify;
	public Runnable enter;
	public int color;
	private int cur0, cur1, curH;
	private boolean mouseSel;

	public TextField(
		GuiGroup gui, int x, int y, int w, int h,
		SpriteModel model, int color,
		Supplier<String> text, Consumer<String> modify, Runnable enter
	) {
		super(x * 4, y * 4, w * 4, h * 4);
		this.gui = gui;
		this.model = model;
		this.color = color;
		this.text = text;
		this.modify = modify;
		this.enter = enter;
	}

	@Override
	public void redraw() {
		int w = x1 - x0, h = y1 - y0;
		drawBlock(gui.sprites, x0 >> 2, y0 >> 2, w >> 2, h >> 2, model.icon);
		String text = this.text.get();
		int l = text.length();
		int tx = (x0 + x1 >> 1) + (model.tx() - l) * 2, ty = y0 + model.ty() * 2 + 1;
		if (gui.focused() == this) {
			cur0 = min(cur0, l);
			cur1 = min(cur1, l);
			if (cur0 == cur1) {
				print(text, color, tx, ty, 4, 6);
				print("|", CURSOR_COLOR, tx + cur1 * 4 - 2, ty, 4, 6);
			} else {
				int i0 = min(cur0, cur1), i1 = max(cur0, cur1);
				print(text.subSequence(0, i0), color, tx, ty, 4, 6);
				print(text.subSequence(i0, i1), HIGHLIGHT_COLOR | color & 31, tx + i0 * 4, ty, 4, 6);
				print(text.subSequence(i1, text.length()), color, tx + i1 * 4, ty, 4, 6);
			}
		} else print(text, color, tx, ty, 4, 6);
		if (gui.hovered() == this)
			addSel(x0, y0, w, h, color);
	}

	@Override
	public void updateHover() {
		gui.markDirty();
	}

	@Override
	public void unfocus() {
		gui.markDirty();
		enter.run();
	}

	@Override
	public boolean onMouseMove(int mx, int my) {
		if (!super.onMouseMove(mx, my)) return false;
		curH = (mx - (x0 + x1 >> 1) >> 1) - model.tx() + 1;
		if (mouseSel) {
			int l = text.get().length();
			if (cur1 != (cur1 = max(min(curH + l >> 1, l), 0)))
				gui.markDirty();
		}
		return true;
	}

	@Override
	public boolean onMouseButton(int button, int action, int mods) {
		if (action == GLFW_PRESS) {
			gui.focus(this);
			int l = text.get().length();
			if (button == GLFW_MOUSE_BUTTON_LEFT) {
				cur0 = cur1 = max(min(curH + l >> 1, l), 0);
				mouseSel = true;
			}
			gui.markDirty();
		} else if (button == GLFW_MOUSE_BUTTON_LEFT)
			mouseSel = false;
		return true;
	}

	@Override
	public boolean onKeyInput(int key, int scancode, int action, int mods) {
		if (action != GLFW_PRESS) return false;
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
		String text = this.text.get();
		int nc, l = text.length();
		switch(key) {
		case GLFW_KEY_HOME:
			cur1 = 0;
			if (!shift) cur0 = cur1;
			break;
		case GLFW_KEY_END:
			cur1 = l;
			if (!shift) cur0 = cur1;
			break;
		case GLFW_KEY_LEFT:
			cur1 = max(cur1 - 1, 0);
			if (!shift) cur0 = cur1;
			break;
		case GLFW_KEY_RIGHT:
			cur1 = min(cur1 + 1, l);
			if (!shift) cur0 = cur1;
			break;
		case GLFW_KEY_A:
			if (!ctrl) return false;
			cur0 = 0;
			cur1 = l;
			break;
		case GLFW_KEY_V:
			if (!ctrl) return false;
			cur0 = min(cur0, l);
			cur1 = min(cur1, l);
			String s = glfwGetClipboardString(Main.WINDOW);
			text = text.substring(0, nc = min(cur0, cur1)).concat(s)
			.concat(text.substring(max(cur0, cur1)));
			cur0 = cur1 = nc + s.length();
			modify.accept(text);
			break;
		case GLFW_KEY_X, GLFW_KEY_C:
			if (!ctrl) return false;
			cur0 = min(cur0, l);
			cur1 = min(cur1, l);
			if (cur0 == cur1) return true;
			glfwSetClipboardString(Main.WINDOW,
				text.subSequence(min(cur0, cur1), max(cur0, cur1))
			);
			if (key == GLFW_KEY_C) return true;
			//else also delete selection
		case GLFW_KEY_DELETE, GLFW_KEY_BACKSPACE:
			cur0 = min(cur0, l);
			cur1 = min(cur1, l);
			if (cur1 == cur0) cur1 = key == GLFW_KEY_BACKSPACE
				? max(cur1 - 1, 0)
				: min(cur1 + 1, l);
			text = text.substring(0, nc = min(cur0, cur1))
			.concat(text.substring(max(cur0, cur1)));
			cur0 = cur1 = nc;
			modify.accept(text);
			break;
		case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER:
			return gui.focus(null);
		default: return false;
		}
		gui.markDirty();
		return true;
	}

	@Override
	public boolean onCharInput(int cp) {
		int nc = min(cur0, cur1);
		String text = this.text.get();
		text = text.substring(0, nc) + (char)cp
			+ text.substring(max(cur0, cur1));
		cur0 = cur1 = nc + 1;
		modify.accept(text);
		gui.markDirty();
		return true;
	}

}
