package cd4017be.glutil;

import static org.lwjgl.opengl.GL32C.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;

/**
 * @author CD4017BE */
public class GLUtils {

	/**@param type shader type
	 * @param path resource to load from
	 * @return new GL shader id or -1 if loading or compiling failed */
	public static int loadShader(int type, String path) {
		try {
			return loadShader(type, GLUtils.class.getResourceAsStream(path));
		} catch(IOException | ShaderCompileException e) {
			e.printStackTrace();
			return -1;
		}
	}

	/**Loads a GLSL shader.
	 * @param type one of {@link GL20C#GL_VERTEX_SHADER GL_VERTEX_SHADER},
	 * {@link GL20C#GL_FRAGMENT_SHADER GL_FRAGMENT_SHADER},
	 * {@link GL32C#GL_GEOMETRY_SHADER GL_GEOMETRY_SHADER}
	 * @param is source to load from
	 * @return new GL shader id
	 * @throws IOException on error reading is
	 * @throws ShaderCompileException if shader couldn't compile */
	public static int loadShader(int type, InputStream is)
	throws IOException, ShaderCompileException {
		if (is == null) throw new FileNotFoundException("input stream is null");
		ArrayList<CharBuffer> buffers = new ArrayList<CharBuffer>();
		try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			int n, m; do {
				char[] buf = new char[256];
				for (n = 0; n < 256; n += m) {
					m = isr.read(buf, n, 256 - n);
					if (m < 0) break;
				}
				buffers.add(CharBuffer.wrap(buf, 0, n));
			} while(n == 256);
		}
		int id = glCreateShader(type);
		glShaderSource(id, buffers.toArray(CharBuffer[]::new));
		glCompileShader(id);
		if (glGetShaderi(id, GL_COMPILE_STATUS) != GL_TRUE) {
			String info = glGetShaderInfoLog(id);
			glDeleteShader(id);
			throw new ShaderCompileException(info);
		}
		return id;
	}

	/**Loads a 2D texture.
	 * @param target
	 * @param path resource to load from
	 * @param fmt internal format for GL texture
	 * @see GL11C#glTexImage2D glTexImage2D(target, 0, fmt, ...) */
	public static void loadTexture2D(int target, String path, int fmt) {
		try {
			loadTexture2D(target, GLUtils.class.getResourceAsStream(path), fmt);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	/**Loads a 2D texture.
	 * @param target
	 * @param is source to load from
	 * @param fmt internal format for GL texture
	 * @throws IOException
	 * @see GL11C#glTexImage2D glTexImage2D(target, 0, fmt, ...) */
	public static void loadTexture2D(int target, InputStream is, int fmt) throws IOException {
		BufferedImage bi = ImageIO.read(is);
		int w = bi.getWidth(), h = bi.getHeight();
		int[] pixels = new int[w * h];
		bi.getRGB(0, 0, w, h, pixels, 0, w);
		glTexImage2D(target, 0, fmt, w, h, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
	}

	/**Setup and enable a vertex attribute.
	 * @param prog shader program id
	 * @param name attribute name
	 * @param type attribute data type
	 * @param size vector entries
	 * @param stride bytes per vertex
	 * @param ofs byte offset
	 * @return attribute index */
	public static int initVertexAttrib(
		int prog, String name, int type,
		int size, int stride, int ofs
	) {
		int idx = glGetAttribLocation(prog, name);
		glEnableVertexAttribArray(idx);
		glVertexAttribPointer(idx, size, type, false, stride, ofs);
		return idx;
	}

	/**Setup and enable a vertex attribute,
	 * normalizing integer values sent to float attributes.
	 * @param prog shader program id
	 * @param name attribute name
	 * @param type attribute data type
	 * @param size vector entries
	 * @param stride bytes per vertex
	 * @param ofs byte offset
	 * @return attribute index */
	public static int initVertexAttribNorm(
		int prog, String name, int type,
		int size, int stride, int ofs
	) {
		int idx = glGetAttribLocation(prog, name);
		glEnableVertexAttribArray(idx);
		glVertexAttribPointer(idx, size, type, true, stride, ofs);
		return idx;
	}

}
