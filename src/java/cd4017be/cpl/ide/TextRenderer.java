package cd4017be.cpl.ide;

import static cd4017be.glutil.GLUtils.*;
import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

import cd4017be.glutil.RenderMode;

/**
 * @author CD4017BE */
public class TextRenderer extends RenderMode {

	private static final float D255 = 1F / 255F;

	public final int vbo, mat;
	private final int tileSize, tileStride, fgColor, bgColor, line;

	public TextRenderer() {
		super("/shaders/text_vert.glsl", "/shaders/text_frag.glsl", "/shaders/text_geom.glsl");
		glBindBuffer(GL_ARRAY_BUFFER, vbo = glGenBuffers());
		initAttribI("charCode", GL_UNSIGNED_BYTE, 1, 0, 0);
		this.tileSize = uniform("tileSize");
		this.tileStride = uniform("tileStride");
		this.fgColor = uniform("fgColor");
		this.bgColor = uniform("bgColor");
		this.line = uniform("lineStride");
		this.mat = uniform("transform");
		use().lineWrap(80).color(0xff000000, 0xffffffff);
		glBindVertexArray(0);
	}

	@Override
	protected void finalize() throws Throwable {
		glDeleteBuffers(vbo);
		super.finalize();
	}

	@Override
	public TextRenderer use() {
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		return (TextRenderer)super.use();
	}

	public TextRenderer font(Font font) {
		glBindTexture(GL_TEXTURE_2D, font.tex);
		glUniform4f(tileSize, font.cw, font.ch, font.cx, font.cy);
		glUniform1ui(tileStride, font.stride);
		return this;
	}

	public TextRenderer lineWrap(int width) {
		glUniform1ui(line, width);
		return this;
	}

	public TextRenderer color(int bg, int fg) {
		setColor(bgColor, bg);
		setColor(fgColor, fg);
		return this;
	}

	private static void setColor(int var, int argb) {
		glUniform4f(var,
			(argb >>> 16 & 255) * D255,
			(argb >>>  8 & 255) * D255,
			(argb        & 255) * D255,
			(argb >>> 24      ) * D255
		);
	}

	public TextRenderer pos(float x, float y, float z, float cw, float ch) {
		glUniformMatrix3x4fv(mat, false, new float[] {
			cw, 0, 0, 0,
			0, ch, 0, 0,
			x, y, z, 1
		});
		return this;
	}

	public int print(String s) {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.ASCII(s, false);
			int n = buf.remaining();
			glBufferData(GL_ARRAY_BUFFER, buf, GL_STREAM_DRAW);
			glDrawArrays(GL_POINTS, 0, n);
			return n;
		}
	}


	public static class Font {

		public final int tex;
		final float cw, ch, cx, cy;
		final int stride;

		public Font(String fontTex, boolean linear, float cw, float ch, int stride) {
			glBindTexture(GL_TEXTURE_2D, tex = glGenTextures());
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
			loadTexture2D(GL_TEXTURE_2D, fontTex, GL_R8);
			int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
			int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
			this.cw = cw;
			this.ch = ch;
			this.cx = linear ? -0.5F / (float)w : 0;
			this.cy = linear ? -0.5F / (float)h : 0;
			this.stride = stride;
		}

		@Override
		protected void finalize() {
			glDeleteTextures(tex);
		}

	}

}
