package cd4017be.dfc.editor;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;
import cd4017be.dfc.lang.LoadingCache;

/**
 * @author CD4017BE */
public class Main {

	private static final String TITLE = "Data flow circuit IDE";

	public static void main(String[] args) {
		glfwSetErrorCallback((err, desc)-> System.out.format("GLFW Error %d: %s\n", err, MemoryUtil.memASCII(desc)));
		if(!glfwInit()) throw new RuntimeException("can't init GLFW");
		//create window and GL context
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		long window = glfwCreateWindow(1024, 768, TITLE, NULL, NULL);
		try {
			if(window == NULL) throw new RuntimeException("can't create window");
			glfwMakeContextCurrent(window);
			System.out.printf("OpenGl %d.%d rev%d%s%s\n",
				glfwGetWindowAttrib(window, GLFW_CONTEXT_VERSION_MAJOR),
				glfwGetWindowAttrib(window, GLFW_CONTEXT_VERSION_MINOR),
				glfwGetWindowAttrib(window, GLFW_CONTEXT_REVISION),
				switch(glfwGetWindowAttrib(window, GLFW_OPENGL_PROFILE)) {
					case GLFW_OPENGL_COMPAT_PROFILE -> " compat";
					case GLFW_OPENGL_CORE_PROFILE -> " core";
					default -> "";
				},
				switch(glfwGetWindowAttrib(window, GLFW_OPENGL_FORWARD_COMPAT)) {
					case GLFW_TRUE -> " forwardcompat";
					default -> "";
				}
			);
			GLCapabilities caps = GL.createCapabilities();
			if (!caps.OpenGL20)
				throw new RuntimeException("OpenGL drivers are outdated! (version 2.0 or above required)");
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

	private static final ArrayDeque<Runnable> ASYNC_EVENTS = new ArrayDeque<>();
	public static final ArrayList<IGuiSection> GUI = new ArrayList<>();
	private static IGuiSection inFocus;
	private static boolean redraw = true, lock;
	public static int WIDTH, HEIGHT;
	public static long WINDOW,
	MAIN_CURSOR, VRESIZE_CURSOR, MOVE_CURSOR, TEXT_CURSOR, SEL_CURSOR;

	static void init(long window) {
		WINDOW = window;
		MAIN_CURSOR = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
		VRESIZE_CURSOR = glfwCreateStandardCursor(GLFW_VRESIZE_CURSOR);
		MOVE_CURSOR = glfwCreateStandardCursor(GLFW_HAND_CURSOR);
		TEXT_CURSOR = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR);
		SEL_CURSOR = glfwCreateStandardCursor(GLFW_CROSSHAIR_CURSOR);
		glfwSetInputMode(window, GLFW_LOCK_KEY_MODS, GLFW_TRUE);
		glClearColor(0, 0, 0, 1);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		LoadingCache.initGraphics();
		new CircuitEditor().open(
			LoadingCache.getModule(Path.of("src/dfc/test"))
			.getBlock("test2")
		);
	}

	private static void run(long window) {
		while(!glfwWindowShouldClose(window) && !GUI.isEmpty()) {
			if (redraw) {
				redraw = false;
				glClear(GL_COLOR_BUFFER_BIT);
				for (IGuiSection gs : GUI) gs.redraw();
				glfwSwapBuffers(window);
			}
			checkGLErrors();
			glfwWaitEvents();
			synchronized(ASYNC_EVENTS) {
				while(!ASYNC_EVENTS.isEmpty())
					ASYNC_EVENTS.poll().run();
			}
		}
	}

	static void close(long window) {
		for (IGuiSection gs : GUI) gs.close();
		Shaders.deleteAll();
	}

	public static void runAsync(Runnable r) {
		synchronized(ASYNC_EVENTS) {
			ASYNC_EVENTS.add(r);
			glfwPostEmptyEvent();
		}
	}

	public static void lockFocus(IGuiSection gui) {
		inFocus = gui;
		lock = gui != null;
	}

	public static void lock(boolean doLock) {
		lock = doLock;
	}

	/**Cause a redraw and frame buffer swap
	 * @param window (ignored) */
	public static void refresh(long window) {
		redraw = true;
	}

	private static void onResize(long window, int w, int h) {
		glViewport(0, 0, w, h);
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
		if (lock && inFocus != null)
			inFocus.onMouseMove(x, y);
		else for (IGuiSection gs : GUI)
			if (gs.onMouseMove(x, y)) inFocus = gs;
	}

	private static final String[] ERROR_MSG = {
		"invalid enum", "invalid value", "invalid operation", null,
		null, "out of memory", "invalid framebuffer operation"
	};

	public static void checkGLErrors() {
		checkGLErrors(1);
	}

	public static void checkGLErrors(int depth) {
		for (int err; (err = glGetError()) != GL_NO_ERROR; ) {
			int i = err - GL_INVALID_ENUM;
			String msg = i >= 0 && i < ERROR_MSG.length ? ERROR_MSG[i] : null;
			System.out.format(
				msg != null ? "OpenGL Error 0x%X: %s @ %s\n"
					: "unknown OpenGL Error code: %X @ %s\n",
				err, msg, Thread.currentThread().getStackTrace()[depth + 2]
			);
		}
	}

	public static void setTitle(String file) {
		glfwSetWindowTitle(WINDOW, file == null ? TITLE : TITLE + " - " + file);
	}

}
