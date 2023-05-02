package cd4017be.dfc.editor.gui;

import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * @author cd4017be */
public class HoverInfo extends HoverRectangle {

	public String text;

	public HoverInfo(int x0, int y0, int x1, int y1, String text) {
		this.text = text;
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
	}

	public void drawOverlay(GuiGroup gui) {
		int w = gui.w() - 4;
		StringBuilder sb = new StringBuilder();
		int tw = 0, th = 1, is = 0, i0 = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\n')
				sb.append(text, i0, (is = i) + 1);
			else {
				if (c == ' ') is = i;
				if (is == i0 || (i + 1 - i0) * 4 <= w)
					continue;
				sb.append(text, i0, is).append('\n');
			}
			tw = max(tw, is - i0);
			i0 = ++is;
			th++;
		}
		sb.append(text, i0, text.length());
		th *= 6;
		tw *= 4;
		int x = max(min(gui.mx, w - tw), 0);
		int y = gui.my - th;
		if (y < 0) y = gui.my + 8;
		addSel(x, y - 2, tw + 4, th + 4, FG_BLUE_SL | BG_BLACK | OVERLAY);
		print(sb, FG_WHITE | OVERLAY, x + 2, y, 4, 6);
	}

}
