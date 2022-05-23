package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.addSel;
import static cd4017be.dfc.editor.Shaders.print;
import static java.lang.Math.max;

import java.util.function.IntConsumer;

/**
 * 
 * @author CD4017BE */
public class Box {

	public final int x0, y0, x1, y1;
	public IntConsumer onClick = b -> {};
	public String text;
	public int fc = 0, hc = 0, tc;

	public Box(int x0, int y0, int w, int h) {
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x0 + w;
		this.y1 = y0 + h;
	}

	public Box click(IntConsumer action, int fc, int hc) {
		this.onClick = action;
		this.fc = fc;
		this.hc = hc;
		return this;
	}

	public Box text(String text, int tc) {
		this.text = text;
		this.tc = tc;
		return this;
	}

	public boolean pointInside(int x, int y) {
		return onClick != null && x >= x0 && x < x1 && y >= y0 && y < y1;
	}

	public void drawFrame(int mx, int my) {
		if (fc == 0) return;
		addSel(x0, y0, x1 - x0, y1 - y0, pointInside(mx, my) ? hc : fc);
	}

	public void drawText(float ofsX, float ofsY, float scaleX, float scaleY) {
		if (text == null) return;
		int w = x1 - x0, h = y1 - y0, l = text.length();
		print(
			text, w, tc, 0,
			ofsX + scaleX * (x0 + max((w - l) * 0.5F, 0)),
			ofsY - scaleY * (y0 + max(h * 0.5F - ((l-1)/w + 1) * 0.75F, 0)),
			scaleX, scaleY * -1.5F
		);
	}

}
