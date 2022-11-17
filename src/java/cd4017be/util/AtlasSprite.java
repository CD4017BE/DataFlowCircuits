package cd4017be.util;

import static java.lang.Math.max;

/**
 * @author CD4017BE */
public class AtlasSprite implements IRect {

	public static final AtlasSprite NULL = new AtlasSprite(0, 0, 0, 0);

	private AtlasSprite a, b;
	private int mw, mh;
	/**texture coordinates */
	public int x, y, w, h, id;

	/**Create a new atlas texture of given size
	 * @param w width
	 * @param h height */
	public AtlasSprite(int w, int h) {
		this.mw = w;
		this.mh = h;
	}

	private AtlasSprite(int x, int y, int w, int h) {
		this(w, h);
		this.x = x;
		this.y = y;
	}

	/**@param sw sprite width
	 * @param sh sprite height
	 * @return an allocated sprite in this texture or null if it doesn't fit */
	public AtlasSprite place(int sw, int sh) {
		int rw = mw - sw, rh = mh - sh;
		if (rw < 0 || rh < 0) return null;
		AtlasSprite n;
		if (a == null) {
			if (rw < rh) {
				a = rw == 0 ? NULL : new AtlasSprite(x + sw, y, rw, sh);
				b =                  new AtlasSprite(x, y + sh, mw, rh);
			} else {
				a = rh == 0 ? NULL : new AtlasSprite(x, y + sh, sw, rh);
				b = rw == 0 ? NULL : new AtlasSprite(x + sw, y, rw, mh);
			}
			w = sw; h = sh; n = this;
		} else if ((n = a.place(sw, sh)) == null && (n = b.place(sw, sh)) == null) return n;
		mw = max(a.mw, b.mw);
		mh = max(a.mh, b.mh);
		return n;
	}

	public int A() {
		return w * h;
	}

	@Override
	public int x() {
		return x;
	}

	@Override
	public int y() {
		return y;
	}

	@Override
	public int w() {
		return w;
	}

	@Override
	public int h() {
		return h;
	}

}
