package cd4017be.dfc.editor;

/**
 * @author CD4017BE */
public interface IGuiSection {

	/**when the window should redraw */
	void redraw();

	/**when this section is removed */
	default void close() {}

	/**when the window resolution changes
	 * @param w new frame buffer width
	 * @param h new frame buffer height */
	default void onResize(int w, int h) {}

	/**@param key GLFW key code
	 * @param scancode keyboard scan code
	 * @param action press, release or repeat
	 * @param mods active key modifiers */
	default void onKeyInput(int key, int scancode, int action, int mods) {}

	/**@param cp the typed in character code point */
	default void onCharInput(int cp) {}

	/**@param button the mouse button
	 * @param action press or release
	 * @param mods active key modifiers */
	default void onMouseButton(int button, int action, int mods) {}

	/**@param dx scroll delta X (unknown scale)
	 * @param dy scroll delta Y (unknown scale)*/
	default void onScroll(double dx, double dy) {}

	/**@param mx mouse X in relative window coordinates: left = 0.0, right = 1.0
	 * @param my mouse Y in relative window coordinates: top = 0.0, bottom = 1.0
	 * @return whether this section is in focus */
	boolean onMouseMove(double mx, double my);
}
