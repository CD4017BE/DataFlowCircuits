package cd4017be.glutil;

import static cd4017be.glutil.GLUtils.loadShader;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL32C.*;

/**
 * @author CD4017BE */
public class RenderMode extends ShaderProgram {

	protected final int vertShader, fragShader, geomShader, vao;

	public RenderMode(String vert, String frag, String geom) {
		super();
		attach(vertShader = loadShader(GL_VERTEX_SHADER, vert));
		attach(fragShader = loadShader(GL_FRAGMENT_SHADER, frag));
		if (geom == null) geomShader = 0;
		else attach(geomShader = loadShader(GL_GEOMETRY_SHADER, geom));
		glBindVertexArray(vao = glGenVertexArrays());
		init();
		link();
	}

	protected void init() {
		bindOutput("outColor", 0);
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		glDeleteShader(fragShader);
		glDeleteShader(vertShader);
		glDeleteShader(geomShader);
		glDeleteVertexArrays(vao);
	}

	@Override
	public RenderMode use() {
		glBindVertexArray(vao);
		return (RenderMode)super.use();
	}

}
