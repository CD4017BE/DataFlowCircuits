package cd4017be.cpl.ide;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryUtil;

/**
 * @author CD4017BE */
public class Main {

	public static long window;
	private static IDrawable menu;

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
		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
		window = glfwCreateWindow(640, 640, "CPL IDE", NULL, NULL);
		try {
			if(window == NULL) throw new RuntimeException("can't create window");
			glfwMakeContextCurrent(window);
			GL.createCapabilities();
			//init rendering
			glfwSwapInterval(1);
			int[] w = new int[1], h = new int[1];
			glfwGetFramebufferSize(window, w, h);
			GL32C.glViewport(0, 0, w[0], h[0]);
			//main loop
			setMenu(new TestMenu());
			while(menu != null && !glfwWindowShouldClose(window)) {
				if (menu.update(window))
					glfwSwapBuffers(window);
				glfwPollEvents();
			}
			//cleanup
			setMenu(null);
			glfwDestroyWindow(window);
		} finally {
			glfwTerminate();
		}
	}

	public static void setMenu(IDrawable menu) {
		if (Main.menu != null)
			Main.menu.close(window);
		Main.menu = menu;
		if (menu == null) return;
		menu.init(window);
	}

}
