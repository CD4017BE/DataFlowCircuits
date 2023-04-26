package cd4017be.dfc.editor.gui;

import static cd4017be.dfc.editor.Main.ICONS;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.ArrayList;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;

import cd4017be.util.VertexArray;

/**
 * 
 * @author CD4017BE */
public class GuiGroup extends HoverRectangle implements Drawable {

	private InputHandler hovered, focused;
	protected InputHandler background;
	public final GuiGroup parent;
	public final ArrayList<InputHandler> inputHandlers = new ArrayList<>();
	public final ArrayList<Drawable> drawables = new ArrayList<>();
	public final VertexArray sprites;
	/** number of frames to redraw (2: both buffers, 1: front buffer, 0: up to date) */
	protected byte redraw;
	/** scale < 0: pre hub, scale == 0: post hub, scale > 0: grid scale factor (active) */
	protected int scale;

	public GuiGroup(GuiGroup parent, int scale) {
		this.parent = parent;
		this.sprites = parent.sprites;
		this.scale = scale;
	}

	public GuiGroup(long window) {
		this.parent = null;
		this.sprites = genBlockVAO(16);
		this.scale = -1;
		glfwSetKeyCallback(window, this::onKeyInput);
		glfwSetCharCallback(window, this::onCharInput);
		glfwSetMouseButtonCallback(window, this::onMouseButton);
		glfwSetScrollCallback(window, this::onScroll);
		glfwSetCursorPosCallback(window, this::onMouseMove);
		glfwSetFramebufferSizeCallback(window, this::onResize);
		glfwSetWindowRefreshCallback(window, this::refresh);
		glfwSetInputMode(window, GLFW_LOCK_KEY_MODS, GLFW_TRUE);
		glClearColor(0, 0, 0, 1);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glEnable(GL_SCISSOR_TEST);
	}

	public void add(Object o) {
		if (o instanceof Drawable d) drawables.add(d);
		if (o instanceof InputHandler ih) inputHandlers.add(ih);
	}

	public InputHandler hovered() {
		return hovered;
	}

	public InputHandler focused() {
		return focused;
	}

	public boolean focus(InputHandler focus) {
		if (focused == focus) return false;
		if (focused != null) focused.unfocus();
		focused = focus;
		return true;
	}

	@Override
	public void unfocus() {
		focus(null);
	}

	@Override
	public void updateHover() {
		InputHandler hover;
		if (parent.hovered != this && (hover = hovered) != (hovered = null))
			hover.updateHover();
	}

	@Override
	public boolean onMouseMove(int mx, int my) {
		InputHandler hover = hovered;
		hovered = null;
		if (!super.onMouseMove(mx, my)) {
			if (hover != null) hover.updateHover();
			return false;
		}
		if (scale > 0) {
			mx = (mx - x0) / scale;
			my = (my - y0) / scale;
		}
		for (int i = inputHandlers.size() - 1; i >= 0; i--) {
			InputHandler ih = inputHandlers.get(i);
			if (ih.onMouseMove(mx, my)) {
				hovered = ih;
				break;
			}
		}
		if (hover != hovered) {
			if (hover != null) hover.updateHover();
			if (hovered != null) hovered.updateHover();
		}
		return true;
	}

	@Override
	public boolean onMouseButton(int button, int action, int mods) {
		if (parent != null) parent.focus(this);
		InputHandler ih = action == GLFW.GLFW_RELEASE ? focused : hovered;
		if (ih != null) return ih.onMouseButton(button, action, mods);
		return focus(null);
	}

	@Override
	public boolean onScroll(double dx, double dy) {
		if (hovered != null && hovered.onScroll(dx, dy)) return true;
		if (focused != null && focused.onScroll(dx, dy)) return true;
		return background != null && background.onScroll(dx, dy);
	}

	@Override
	public boolean onKeyInput(int key, int scancode, int action, int mods) {
		if (focused != null && focused.onKeyInput(key, scancode, action, mods)) return true;
		if (hovered != null && hovered.onKeyInput(key, scancode, action, mods)) return true;
		return background != null && background.onKeyInput(key, scancode, action, mods);
	}

	@Override
	public boolean onCharInput(int cp) {
		return focused != null && focused.onCharInput(cp);
	}

	@Override
	public void redraw() {
		if (scale <= 0) {
			redraw = 0;
			for (Drawable d : drawables)
				d.redraw();
		} else if (redraw > 0) {
			redraw--;
			int w = x1 - x0, h = y1 - y0;
			glViewport(x0, y0, w, h);
			glScissor(x0, y0, w, h);
			glClear(GL_COLOR_BUFFER_BIT);
			sprites.clear();
			for (Drawable d : drawables)
				d.redraw();
			float sx = 2F / w * scale, sy = -2F / h * scale;
			ICONS.bind();
			transform(block_transform, -1, 1, sx * 4, sy * 4);
			sprites.draw();
			drawSel(-1, 1, sx, sy, 0, 2);
			drawText(-1, 1, sx, sy);
		}
	}

	public void markDirty() {
		redraw = 2;
		if (parent != null) parent.markDirty();
	}

	public boolean isDirty() {
		return redraw > 0;
	}

	private void onKeyInput(long window, int key, int scancode, int action, int mods) {
		onKeyInput(key, scancode, action, mods);
	}

	private void onCharInput(long window, int cp) {
		onCharInput(cp);
	}

	private void onMouseButton(long window, int button, int action, int mods) {
		onMouseButton(button, action, mods);
	}

	private void onScroll(long window, double dx, double dy) {
		onScroll(dx, dy);
	}

	private void onMouseMove(long window, double x, double y) {
		int[] w = {0}, h = {0};
		glfwGetWindowSize(window, w, h);
		onMouseMove((int)(x / w[0] * x1), (int)(y / h[0] * y1));
	}

	public void onResize(long window, int w, int h) {
		if (scale > 0) markDirty();
		else for (Drawable d : drawables)
			if (d instanceof GuiGroup g)
				g.onResize(window, w, h);
		if (parent == null) {
			x1 = w; y1 = h;
		}
	}

	protected void refresh(long window) {
		if (scale > 0) markDirty();
		else for (Drawable d : drawables)
			if (d instanceof GuiGroup g)
				g.refresh(window);
	}

	public void close(long window) {
		for (Drawable d : drawables)
			if (d instanceof GuiGroup g)
				g.close(window);
		drawables.clear();
		inputHandlers.clear();
	}

	protected void scaleCentered(int sw, int sh, int gw, int gh) {
		scale = max(1, min(sw / gw, sh / gh));
		x0 = sw - gw * scale >> 1;
		x1 = sw + gw * scale >> 1;
		y0 = sh - gh * scale >> 1;
		y1 = sh + gh * scale >> 1;
		markDirty();
	}

}
