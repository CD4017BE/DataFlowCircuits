package cd4017be.dfc.editor.gui;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.lwjgl.glfw.GLFW.*;

import java.util.ArrayList;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.graphics.SpriteModel;

/**
 * 
 * @author CD4017BE */
public class TextField extends HoverRectangle implements Drawable, InputHandler {

	public static final SpriteModel TEXT_MODEL = Main.ICONS.get("/textures/textfield.tga");

	public final GuiGroup gui;
	public SpriteModel model = TEXT_MODEL;
	public String text = "";
	public TextAction action = NOP;
	public int color = FG_YELLOW_L;
	private int cur0, cur1, curH, selAC;
	private boolean mouseSel;
	public final ArrayList<String> autocomplete = new ArrayList<>();

	public TextField(GuiGroup gui) {
		this.gui = gui;
		gui.drawables.add(this);
		gui.inputHandlers.add(this);
	}

	public TextField pos(int x, int y, int w, int h) {
		x0 = x;
		y0 = y;
		x1 = x + w;
		y1 = y + h;
		gui.markDirty();
		return this;
	}

	public TextField text(String text) {
		this.text = text;
		gui.markDirty();
		return this;
	}

	public TextField model(SpriteModel model) {
		this.model = model;
		gui.markDirty();
		return this;
	}

	public TextField color(int color) {
		this.color = color;
		gui.markDirty();
		return this;
	}

	public TextField action(TextAction action) {
		this.action = action;
		return this;
	}

	public int cursor() {
		return cur1;
	}

	public void cursor(int c) {
		cur1 = cur0 = max(min(c, text.length()), 0);
		gui.markDirty();
	}

	@Override
	public void redraw() {
		int w = x1 - x0, h = y1 - y0, l = text.length(), tx, ty;
		if (model != null) {
			drawBlock(gui.sprites, x0 >> 2, y0 >> 2, w >> 2, h >> 2, model.icon);
			tx = (x0 + x1 >> 2) - l + model.tx();
			ty = (y0 >> 1) + model.ty();
		} else {
			tx = (x0 + x1 >> 2) - l;
			ty = (y0 + y1 >> 2) - 2;
		}
		if (gui.focused() == this) {
			cur0 = min(cur0, l);
			cur1 = min(cur1, l);
			if (cur0 == cur1) {
				print(text, color, tx, ty, 2, 3);
				print("|", CURSOR_COLOR, tx + cur1 * 2 - 1, ty, 2, 3);
			} else {
				int i0 = min(cur0, cur1), i1 = max(cur0, cur1);
				print(text.subSequence(0, i0), color, tx, ty, 2, 3);
				print(text.subSequence(i0, i1), HIGHLIGHT_COLOR | color & 31, tx + i0 * 2, ty, 2, 3);
				print(text.subSequence(i1, text.length()), color, tx + i1 * 2, ty, 2, 3);
			}
			if (!autocomplete.isEmpty()) {
				int gh = gui.h(), ay0, n;
				if (y0 > gh - y1) {
					n = min(y0 / 6, autocomplete.size());
					ay0 = (y0 >> 1) - n * 3;
				} else {
					n = min((gh - y1) / 6, autocomplete.size());
					ay0 = y1 >> 1;
				}
				int aw = w >> 1;
				for (int i = 0; i < n; i++)
					aw = max(aw, autocomplete.get(i).length() * 2);
				int ax0 = (x0 + x1 >> 1) - aw >> 1;
				addSel(ax0, ay0, aw, n * 3 + 1, FG_GRAY_L | BG_BLACK | OVERLAY);
				if (selAC >= 0 && selAC < n)
					addSel(ax0, ay0 + selAC * 3, aw, 4, FG_GREEN_L | OVERLAY);
				for (int i = 0; i < n; i++) {
					String s = autocomplete.get(i);
					print(s, color & 31 | OVERLAY, (x0 + x1 >> 2) - s.length(), ay0 + i * 3, 2, 3);
				}
			}
		} else print(text, color, tx, ty, 2, 3);
		if (gui.hovered() == this)
			addSel(x0 >> 1, y0 >> 1, w >> 1, h >> 1, color);
	}

	@Override
	public void updateHover() {
		glfwSetCursor(WINDOW, gui.hovered() == this ? TEXT_CURSOR : MAIN_CURSOR);
		gui.markDirty();
	}

	@Override
	public void unfocus() {
		gui.markDirty();
		action.handle(this, true);
	}

	@Override
	public boolean onMouseMove(int mx, int my) {
		if (!super.onMouseMove(mx, my)) return false;
		curH = mx - (x0 + x1 >> 1) - (model == null ? 0 : model.tx() * 2) + 2;
		if (y1 - y0 >= 8) curH >>= 1;
		if (mouseSel) {
			int l = text.length();
			if (cur1 != (cur1 = max(min(curH + l >> 1, l), 0)))
				gui.markDirty();
		}
		return true;
	}

	@Override
	public boolean onMouseButton(int button, int action, int mods) {
		if (action == GLFW_PRESS) {
			gui.focus(this);
			int l = text.length();
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
		if (action == GLFW_RELEASE) return false;
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
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
		case GLFW_KEY_UP:
			selAC = (selAC <= 0 ? autocomplete.size() : selAC) - 1;
			break;
		case GLFW_KEY_DOWN:
			selAC = selAC + 1 >= autocomplete.size() ? 0 : selAC + 1;
			break;
		case GLFW_KEY_TAB:
			if (selAC >= 0 && selAC < autocomplete.size()) {
				String s = autocomplete.get(selAC);
				for (int i = max(0, l - s.length()); i <= l; i++)
					if (s.regionMatches(0, text, i, l - i)) {
						text = text.substring(0, i).concat(s);
						cur1 = cur0 = text.length();
						this.action.handle(this, false);
						break;
					}
				break;
			} else return false;
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
			this.action.handle(this, false);
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
			this.action.handle(this, false);
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
		text = text.substring(0, nc) + (char)cp
			+ text.substring(max(cur0, cur1));
		cur0 = cur1 = nc + 1;
		this.action.handle(this, false);
		gui.markDirty();
		return true;
	}

	public static final TextAction NOP = (tf, finish) -> {};

	@FunctionalInterface
	public interface TextAction {
		void handle(TextField tf, boolean finish);
	}

}
