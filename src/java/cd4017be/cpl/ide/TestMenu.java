package cd4017be.cpl.ide;

import static org.lwjgl.glfw.GLFW.*;

import static org.lwjgl.opengl.GL32C.*;

import cd4017be.cpl.ide.TextRenderer.Font;

/**
 * @author CD4017BE */
public class TestMenu implements IDrawable {

	Font font;
	TextRenderer tr;

	@Override
	public boolean update(long window) {
		glClear(GL_COLOR_BUFFER_BIT);
		tr.use().font(font)
		.pos(-1F, 1F, 0F, 0.1F, -0.2F)
		.color(0xff0000ff, 0xffffffff)
		.print(String.format("%tT", System.currentTimeMillis()));
		return true;
	}

	@Override
	public void init(long window) {
		glfwSetKeyCallback(window, this::onKeyInput);
		glfwSetCharCallback(window, this::onCharInput);
		glfwSetCursorPosCallback(window, this::onMouseMove);
		glfwSetMouseButtonCallback(window, this::onMouseInput);
		glfwSetScrollCallback(window, this::onScrollInput);
		glfwSetDropCallback(window, this::onFileDrop);
		
		glClearColor(0, 0, 0, 1);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		font = new Font("/textures/font.bmp", true, 1F/16F, 1.5F/16F, 16);
		tr = new TextRenderer();
	}

	@Override
	public void close(long window) {
		glfwSetKeyCallback(window, null);
		glfwSetCharCallback(window, null);
		glfwSetCursorPosCallback(window, null);
		glfwSetMouseButtonCallback(window, null);
		glfwSetScrollCallback(window, null);
		glfwSetDropCallback(window, null);
		
		font = null;
		tr = null;
	}

	void onKeyInput(long window, int key, int scancode, int action, int mods) {
		
	}

	void onCharInput(long window, int codepoint) {
		
	}

	void onMouseMove(long window, double xpos, double ypos) {
		
	}

	void onMouseInput(long window, int button, int action, int mods) {
		
	}

	void onScrollInput(long window, double xoffset, double yoffset) {
		
	}

	void onFileDrop(long window, int count, long paths) {
		
	}
}
