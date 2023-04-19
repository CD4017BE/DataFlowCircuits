package cd4017be.dfc.editor.gui;

/**
 * 
 * @author CD4017BE */
public interface InputHandler {

	/**Triggered when in focus or hovered and key pressed or released.
	 * @param key GLFW key code
	 * @param scancode keyboard scan code
	 * @param action press, release or repeat
	 * @param mods active key modifiers
	 * @return consume event */
	default boolean onKeyInput(int key, int scancode, int action, int mods) {return false;}

	/**Triggered when in focus and character typed.
	 * @param cp the typed in character code point
	 * @return consume event */
	default boolean onCharInput(int cp) {return false;}

	/**Triggered when hovered and button pressed or when in focus and button released.
	 * @param button the mouse button
	 * @param action press or release
	 * @param mods active key modifiers
	 * @return consume event */
	default boolean onMouseButton(int button, int action, int mods) {return false;}

	/**Triggered when hovered and scrolling.
	 * @param dx scroll delta X (unknown scale)
	 * @param dy scroll delta Y (unknown scale)
	 * @return consume event */
	default boolean onScroll(double dx, double dy) {return false;}

	/**Triggered when mouse moved.
	 * @param mx mouse X in gui coordinates (left to right)
	 * @param my mouse Y in gui coordinates (top to bottom)
	 * @return whether this component is hovered */
	boolean onMouseMove(int mx, int my);

	/**Triggered when focus lost. */
	default void unfocus() {}

	/**Triggered when hovered or unhovered. */
	default void updateHover() {}

}
