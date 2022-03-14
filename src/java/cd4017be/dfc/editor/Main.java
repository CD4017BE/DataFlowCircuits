package cd4017be.dfc.editor;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.ArrayList;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

/**
 * @author CD4017BE */
public class Main {

	public static void main(String[] args) {
		glfwSetErrorCallback((err, desc)-> System.out.format("GLFW Error %d: %s\n", err, MemoryUtil.memASCII(desc)));
		if(!glfwInit()) throw new RuntimeException("can't init GLFW");
		//API settings
		glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
		glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
		//create window and GL context
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		long window = glfwCreateWindow(1536, 1024, "Data flow circuit IDE", NULL, NULL);
		try {
			if(window == NULL) throw new RuntimeException("can't create window");
			glfwMakeContextCurrent(window);
			GL.createCapabilities();
			//init rendering
			glfwSwapInterval(1);
			init(window);
			glfwSetKeyCallback(window, Main::onKeyInput);
			glfwSetCharCallback(window, Main::onCharInput);
			glfwSetMouseButtonCallback(window, Main::onMouseButton);
			glfwSetCursorPosCallback(window, Main::onMouseMove);
			glfwSetFramebufferSizeCallback(window, Main::onResize);
			glfwSetScrollCallback(window, Main::onScroll);
			glfwSetWindowRefreshCallback(window, Main::refresh);
			{
				int[] w = new int[1], h = new int[1];
				glfwGetFramebufferSize(window, w, h);
				onResize(window, w[0], h[0]);
			}
			//main loop
			run(window);
			//cleanup
			close(window);
			glfwDestroyWindow(window);
		} finally {
			glfwTerminate();
		}
	}

	public static final ArrayList<IGuiSection> GUI = new ArrayList<>();
	private static IGuiSection inFocus;
	private static boolean redraw = true;
	public static int WIDTH, HEIGHT;

	static void init(long window) {
		glfwSetInputMode(window, GLFW_LOCK_KEY_MODS, GLFW_TRUE);
		glClearColor(0, 0, 0, 1);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		
		GUI.add(new Circuit());
	}

	private static void run(long window) {
		while(!glfwWindowShouldClose(window)) {
			if (redraw) {
				redraw = false;
				glClear(GL_COLOR_BUFFER_BIT);
				for (IGuiSection gs : GUI) gs.redraw();
				glfwSwapBuffers(window);
			}
			checkGLErrors();
			glfwWaitEvents();
		}
	}

	static void close(long window) {
		for (IGuiSection gs : GUI) gs.close();
		Shaders.deleteAll();
	}

	/**Cause a redraw and frame buffer swap
	 * @param window (ignored) */
	public static void refresh(long window) {
		redraw = true;
	}

	private static void onResize(long window, int w, int h) {
		GL11C.glViewport(0, 0, w, h);
		for (IGuiSection gs : GUI) gs.onResize(w, h);
		WIDTH = w; HEIGHT = h;
		refresh(window);
	}

	private static void onKeyInput(long window, int key, int scancode, int action, int mods) {
		if (inFocus != null) inFocus.onKeyInput(key, scancode, action, mods);
	}

	private static void onCharInput(long window, int cp) {
		if (inFocus != null) inFocus.onCharInput(cp);
	}

	private static void onMouseButton(long window, int button, int action, int mods) {
		if (inFocus != null) inFocus.onMouseButton(button, action, mods);
	}

	private static void onScroll(long window, double dx, double dy) {
		if (inFocus != null) inFocus.onScroll(dx, dy);
	}

	private static void onMouseMove(long window, double x, double y) {
		int[] w = {0}, h = {0};
		glfwGetWindowSize(window, w, h);
		x = x / w[0] * 2.0 - 1.0;
		y = y / h[0] * 2.0 - 1.0;
		for (IGuiSection gs : GUI)
			if (gs.onMouseMove(x, y)) inFocus = gs;
	}

	private static final String[] ERROR_MSG = {
		"invalid enum", "invalid value", "invalid operation", null,
		null, "out of memory", "invalid framebuffer operation"
	};

	public static void checkGLErrors() {
		for (int err; (err = GL11C.glGetError()) != GL11C.GL_NO_ERROR; ) {
			int i = err - GL11C.GL_INVALID_ENUM;
			String msg = i >= 0 && i < ERROR_MSG.length ? ERROR_MSG[i] : null;
			System.out.format(
				msg != null ? "OpenGL Error 0x%X: %s @ %s\n"
					: "unknown OpenGL Error code: %X @ %s\n",
				err, msg, Thread.currentThread().getStackTrace()[2]
			);
		}
	}

}
