package cd4017be.util;

import static java.lang.Math.min;
import static org.lwjgl.opengl.GL32C.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryStack;

/**Static helper methods for OpenGL rendering.
 * @author CD4017BE */
public class GLUtils {

	public static final float D255 = 1F / 255F;

	/**@param type shader type
	 * @param path resource to load from
	 * @return new GL shader id or -1 if loading or compiling failed */
	public static int loadShader(int type, String path) {
		try {
			return loadShader(type, GLUtils.class.getResourceAsStream(path));
		} catch(IOException | ShaderCompileException e) {
			System.out.printf("%s: %s", path, e);
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
	public static void loadTexture2D(int target, String path, int fmt) throws IOException {
		InputStream is = GLUtils.class.getResourceAsStream(path);
		if (is == null) throw new FileNotFoundException(path);
		loadTexture2D(target, is, fmt);
		is.close();
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

	/**@param vert vertex shader id
	 * @param geom geometry shader id
	 * @param frag fragment shader id
	 * @param out output color variables in fragment shader
	 * @return new shader program id */
	public static int program(int vert, int geom, int frag, String... out) {
		int p = glCreateProgram();
		if (vert >= 0) glAttachShader(p, vert);
		if (geom >= 0) glAttachShader(p, geom);
		if (frag >= 0) glAttachShader(p, frag);
		for (int i = 0; i < out.length; i++)
			glBindFragDataLocation(p, i, out[i]);
		glLinkProgram(p);
		System.out.println(glGetProgramInfoLog(p));
		return p;
	}

	public static void loadTexture(ByteBuffer img, int target, int fmt) {
		if (img == null) return;
		glTexImage2D(
			target, 0, fmt, img.getShort(), img.getShort(), 0,
			img.getChar(), img.getChar(), img
		);
	}

	/**Load and configure a 2D texture
	 * @param filter texture filter mode
	 * @param wrap boundary behavior
	 * @param format internal format for GL
	 * @param path file path to load
	 * @return GL texture id */
	public static int texture2D(int filter, int wrap, int format, String path) {
		int tex = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, tex);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
		try(MemoryStack ms = MemoryStack.stackPush()) {
			loadTexture(readImage(path), GL_TEXTURE_2D, format);
		}
		return tex;
	}

	/**@param var uniform index
	 * @param argb 0xAARRGGBB color */
	public static void setColor(int var, int argb) {
		glUniform4f(var,
			(argb >>> 16 & 255) * D255,
			(argb >>>  8 & 255) * D255,
			(argb        & 255) * D255,
			(argb >>> 24      ) * D255
		);
	}

	/**Draw a vertex array with a given shader.
	 * @param shaderProg shader program id
	 * @param vertArr vertex array id
	 * @param mode polygon mode
	 * @param count number of vertices to draw */
	public static void draw(int shaderProg, int vertArr, int mode, int count) {
		glUseProgram(shaderProg);
		glBindVertexArray(vertArr);
		glDrawArrays(mode, 0, count);
	}

	/**@param path local texture path
	 * @return image data: {S w, S h, US gl_format, US gl_type, (w * h * T) pixel data}
	 * allocated from {@link MemoryStack} or null if can't load */
	public static ByteBuffer readImage(String path) {
		path = "/textures/" + path + ".im";
		try (InputStream is = GLUtils.class.getResourceAsStream(path)) {
			if (is == null) throw new FileNotFoundException(path);
			return readImage(is);
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static final int[] FORMATS = {
		GL_RED | GL_UNSIGNED_BYTE << 16,
		GL_RED | GL_UNSIGNED_SHORT << 16,
		GL_RED | GL_UNSIGNED_INT << 16,
		GL_RED | GL_DOUBLE << 16,
		GL_GREEN | GL_UNSIGNED_BYTE << 16,
		GL_GREEN | GL_UNSIGNED_SHORT << 16,
		GL_GREEN | GL_UNSIGNED_INT << 16,
		GL_GREEN | GL_DOUBLE << 16,
		GL_BLUE | GL_UNSIGNED_BYTE << 16,
		GL_BLUE | GL_UNSIGNED_SHORT << 16,
		GL_BLUE | GL_UNSIGNED_INT << 16,
		GL_BLUE | GL_DOUBLE << 16,
		GL_ALPHA | GL_UNSIGNED_BYTE << 16,
		GL_ALPHA | GL_UNSIGNED_SHORT << 16,
		GL_ALPHA | GL_UNSIGNED_INT << 16,
		GL_ALPHA | GL_DOUBLE << 16,
		0,
		GL_RG | GL_UNSIGNED_BYTE << 16,
		GL_RG | GL_UNSIGNED_SHORT << 16,
		GL_RG | GL_UNSIGNED_INT << 16,
		GL_BGR | GL_UNSIGNED_BYTE_2_3_3_REV << 16,
		GL_BGR | GL_UNSIGNED_SHORT_5_6_5_REV << 16,
		GL_RG | GL_UNSIGNED_INT_24_8 << 16,
		GL_RG | GL_FLOAT << 16,
		0,
		GL_BGRA | GL_UNSIGNED_SHORT_4_4_4_4_REV << 16,
		GL_BGRA | GL_UNSIGNED_INT_8_8_8_8_REV << 16,
		GL_BGRA | GL_UNSIGNED_SHORT << 16,
		0,
		GL_BGRA | GL_UNSIGNED_SHORT_1_5_5_5_REV << 16,
		GL_BGRA | GL_UNSIGNED_INT_2_10_10_10_REV << 16,
		GL_BGRA | GL_HALF_FLOAT << 16,
	};

	/**Loads an image encoded in a custom format.
	 * @param is image source
	 * @return image data: {S w, S h, US gl_format, US gl_type, (w * h * T) pixel data}
	 * allocated from {@link MemoryStack}.
	 * @throws IOException */
	public static ByteBuffer readImage(InputStream is) throws IOException {
		int f = is.read(), w = is.read(), h = is.read();
		if ((f | w | h) < 0) throw new EOFException();
		int fmt = FORMATS[f & 31];
		if (fmt == 0) throw new IOException("unsupported color format: " + (f & 31));
		int e = f & 3, l = ++w * ++h << e;
		ByteBuffer img = MemoryStack.stackMalloc(8 + l);
		img.putShort((short)w).putShort((short)h).putInt(fmt);
		if ((byte)f < 0) {
			int p = is.read();
			if (p < 0) throw new EOFException();
			int bits = 32 - Integer.numberOfLeadingZeros(p++);
			int mask = (1 << bits) - 1;
			byte[] table = new byte[(mask + 1) << e];
			is.readNBytes(table, 0, p << e);
			for (int b = 0, d = 0; img.hasRemaining(); d >>>= bits, b -= bits) {
				if (b < bits) {
					d |= is.read() << b; b += 8;
					if (d < 0) throw new EOFException();
				}
				img.put(table, (d & mask) << e, 1 << e);
			}
		} else {
			byte[] buf = new byte[min(l, 1024)];
			while((l = img.remaining()) > 0) {
				int n = is.read(buf, 0, min(l, 1024));
				if (n < 0) throw new EOFException();
				img.put(buf, 0, n);
			}
		}
		return img.flip();
	}

}
