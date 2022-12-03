package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.CURSOR_COLOR;
import static cd4017be.dfc.editor.Shaders.HIGHLIGHT_COLOR;
import static cd4017be.dfc.editor.Shaders.print;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.lwjgl.glfw.GLFW.*;

import java.util.function.Consumer;

/**
 * @author CD4017BE */
public class TextField {

	private final Consumer<String> receiver;
	private String text = "";
	private int cur0, cur1;

	public TextField(Consumer<String> receiver) {
		this.receiver = receiver;
	}

	public void set(String text, int c) {
		this.text = text;
		cur0 = cur1 = c < 0 ? text.length() : min(c, text.length());
	}

	public int cursor() {
		return cur1;
	}

	public String get() {
		return text;
	}

	public void redraw(
		int x, int y, int sx, int sy, int c
	) {
		if (c != 0)
			print(text, c, x, y, sx, sy);
		if (cur0 != cur1) {
			int i0 = min(cur0, cur1), i1 = max(cur0, cur1);
			print(text.subSequence(i0, i1), HIGHLIGHT_COLOR, x + i0 * sx, y, sx, sy);
		} else print("|", CURSOR_COLOR, x + cur1 * sx - sx / 2, y, sx, sy);
	}

	public boolean onKeyInput(int key, int mods) {
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
		int nc;
		switch(key) {
		case GLFW_KEY_HOME:
			cur1 = 0;
			if (!shift) cur0 = cur1;
			break;
		case GLFW_KEY_END:
			cur1 = text.length();
			if (!shift) cur0 = cur1;
			break;
		case GLFW_KEY_LEFT:
			cur1 = max(cur1 - 1, 0);
			if (!shift) cur0 = cur1;
			break;
		case GLFW_KEY_RIGHT:
			cur1 = min(cur1 + 1, text.length());
			if (!shift) cur0 = cur1;
			break;
		case GLFW_KEY_X:
			if (!ctrl || cur0 == cur1) return false;
			glfwSetClipboardString(Main.WINDOW,
				text.subSequence(min(cur0, cur1), max(cur0, cur1))
			);
		case GLFW_KEY_DELETE, GLFW_KEY_BACKSPACE:
			if (cur1 == cur0) cur1 = key == GLFW_KEY_BACKSPACE
				? max(cur1 - 1, 0)
				: min(cur1 + 1, text.length());
			text = text.substring(0, nc = min(cur0, cur1))
			.concat(text.substring(max(cur0, cur1)));
			cur0 = cur1 = nc;
			receiver.accept(text);
			break;
		case GLFW_KEY_C:
			if (!ctrl || cur0 == cur1) return false;
			glfwSetClipboardString(Main.WINDOW,
				text.subSequence(min(cur0, cur1), max(cur0, cur1))
			);
			return true;
		case GLFW_KEY_V:
			if (!ctrl) return false;
			String s = glfwGetClipboardString(Main.WINDOW);
			text = text.substring(0, nc = min(cur0, cur1)).concat(s)
			.concat(text.substring(max(cur0, cur1)));
			cur0 = cur1 = nc + s.length();
			receiver.accept(text);
			break;
		case GLFW_KEY_A:
			if (!ctrl) return false;
			cur0 = 0;
			cur1 = text.length();
			break;
		default: return false;
		}
		Main.refresh(0);
		return true;
	}

	public void onCharInput(int cp) {
		int nc = min(cur0, cur1);
		text = text.substring(0, nc) + (char)cp
			+ text.substring(max(cur0, cur1));
		cur0 = cur1 = nc + 1;
		Main.refresh(0);
		receiver.accept(text);
	}

}
