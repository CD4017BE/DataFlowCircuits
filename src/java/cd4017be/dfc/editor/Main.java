package cd4017be.dfc.editor;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.util.ArrayDeque;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

import cd4017be.dfc.editor.gui.GuiGroup;
import cd4017be.dfc.graphics.IconAtlas;
import cd4017be.util.TraceAtlas;

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
			WINDOW = window;
			ICONS = new IconAtlas(Shaders.blockP, 2, 16, 16, 256);
			TRACES = new TraceAtlas(Shaders.traceP, 8, 256);
			MAIN_CURSOR = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
			VRESIZE_CURSOR = glfwCreateStandardCursor(GLFW_VRESIZE_CURSOR);
			MOVE_CURSOR = glfwCreateStandardCursor(GLFW_HAND_CURSOR);
			TEXT_CURSOR = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR);
			SEL_CURSOR = glfwCreateStandardCursor(GLFW_CROSSHAIR_CURSOR);
			GuiGroup gui = new GuiGroup(window);
			init(gui, window);
			//main loop
			run(gui, window);
			//cleanup
			gui.close(window);
			Shaders.deleteAll();
			glfwDestroyWindow(window);
		} finally {
			glfwTerminate();
		}
	}

	private static final ArrayDeque<Runnable> ASYNC_EVENTS = new ArrayDeque<>();
	public static IconAtlas ICONS;
	public static TraceAtlas TRACES;
	public static long WINDOW,
	MAIN_CURSOR, VRESIZE_CURSOR, MOVE_CURSOR, TEXT_CURSOR, SEL_CURSOR;

	static void init(GuiGroup gui, long window) {
		new Test(gui);
//		new CircuitEditor().open(
//			LoadingCache.getModule("test")
//			.getBlock("test2")
//		);
		int[] w = new int[1], h = new int[1];
		glfwGetFramebufferSize(window, w, h);
		gui.onResize(window, w[0], h[0]);
	}

	private static void run(GuiGroup gui, long window) {
		while(!glfwWindowShouldClose(window)) {
			if (gui.isDirty()) {
				gui.redraw();
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

	public static void runAsync(Runnable r) {
		synchronized(ASYNC_EVENTS) {
			ASYNC_EVENTS.add(r);
			glfwPostEmptyEvent();
		}
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
