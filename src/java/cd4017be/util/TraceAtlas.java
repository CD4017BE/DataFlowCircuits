package cd4017be.util;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static org.lwjgl.opengl.GL20C.*;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import cd4017be.compiler.Module;

/**
 * 
 * @author CD4017BE */
public class TraceAtlas {

	private final int texId, shader, texScale;
	private int traceCount;

	public TraceAtlas(int shader, int w0, int h0) {
		glBindTexture(GL_TEXTURE_2D, this.texId = glGenTextures());
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w0, h0 * 4, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, MemoryUtil.NULL);
		checkGLErrors();
		this.shader = shader;
		this.texScale = glGetUniformLocation(shader, "texScale");
		updateShader();
		glUniform1i(glGetUniformLocation(shader, "tex"), 2);
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	private void updateShader() {
		glUseProgram(shader);
		glUniform2f(texScale,
			4F / (float)glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH),
			2F / (float)glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT)
		);
		checkGLErrors(1);
	}

	@Override
	protected void finalize() {
		glDeleteTextures(texId);
	}

	public void bind() {
		glActiveTexture(GL_TEXTURE2);
		glBindTexture(GL_TEXTURE_2D, texId);
		glActiveTexture(GL_TEXTURE0);
		glUseProgram(shader);
	}

	public int load(ByteBuffer img, Module module) {
		glBindTexture(GL_TEXTURE_2D, texId);
		int w = img.getShort(), h = img.getShort();
		ensureSize(traceCount * 4 + h);
		glTexSubImage2D(
			GL_TEXTURE_2D, 0, 0, traceCount << 2, w, h,
			img.getChar(), img.getChar(), img
		);
		glBindTexture(GL_TEXTURE_2D, 0);
		checkGLErrors();
		int i = traceCount; traceCount += h >> 2;
		return i;
	}

	private void ensureSize(int h) {
		int th = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
		if (h <= th) return;
		throw new UnsupportedOperationException("TODO implement trace atlas enlarging");
	}

}
