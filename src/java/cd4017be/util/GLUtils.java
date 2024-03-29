package cd4017be.util;

import static java.lang.Math.min;
import static org.lwjgl.opengl.GL20C.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.lwjgl.opengl.GL20C;
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
	 * {@link GL20C#GL_FRAGMENT_SHADER GL_FRAGMENT_SHADER}
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
	 * @param frag fragment shader id
	 * @return new shader program id */
	public static int program(int vert, int frag) {
		int p = glCreateProgram();
		if (vert >= 0) glAttachShader(p, vert);
		if (frag >= 0) glAttachShader(p, frag);
		glLinkProgram(p);
		//System.out.println(glGetProgramInfoLog(p));
		return p;
	}

	public static void loadTexture(ByteBuffer img, int target, int fmt) {
		loadTexture(img, target, 0, fmt);
	}

	public static void loadTexture(ByteBuffer img, int target, int lvl, int fmt) {
		if (img == null) return;
		glTexImage2D(
			target, lvl, fmt, img.getShort(), img.getShort(), 0,
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
			loadTexture(readImage(path, ms), GL_TEXTURE_2D, format);
		}
		return tex;
	}

	/**Load and configure a 2D texture
	 * @param minFilter texture minification filter mode
	 * @param magFilter texture magnification filter mode
	 * @param wrap boundary behavior
	 * @param format internal format for GL
	 * @param paths file paths of mip-map levels to load (from highest to lowest LOD)
	 * @return GL texture id */
	public static int texture2DMM(int minFilter, int magFilter, int wrap, int format, String... paths) {
		int tex = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, tex);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, paths.length - 1);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
		for (int i = 0; i < paths.length; i++)
			try(MemoryStack ms = MemoryStack.stackPush()) {
				loadTexture(readImage(paths[i], ms), GL_TEXTURE_2D, i, format);
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

	/**@param path local texture path
	 * @return image data: {S w, S h, US gl_format, US gl_type, (w * h * T) pixel data}
	 * allocated from {@link MemoryStack} or null if can't load */
	public static ByteBuffer readImage(String path, MemoryStack ms) {
		path = "/textures/" + path + ".tga";
		try (InputStream is = GLUtils.class.getResourceAsStream(path)) {
			if (is == null) throw new FileNotFoundException(path);
			return readTGA(is, ms);
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
		0, //GL_RG | GL_UNSIGNED_BYTE << 16,
		0, //GL_RG | GL_UNSIGNED_SHORT << 16,
		0, //GL_RG | GL_UNSIGNED_INT << 16,
		GL_BGR | GL_UNSIGNED_BYTE_2_3_3_REV << 16,
		GL_BGR | GL_UNSIGNED_SHORT_5_6_5_REV << 16,
		0, //GL_RG | GL_UNSIGNED_INT_24_8 << 16,
		0, //GL_RG | GL_FLOAT << 16,
		0,
		GL_BGRA | GL_UNSIGNED_SHORT_4_4_4_4_REV << 16,
		GL_BGRA | GL_UNSIGNED_INT_8_8_8_8_REV << 16,
		GL_BGRA | GL_UNSIGNED_SHORT << 16,
		0,
		GL_BGRA | GL_UNSIGNED_SHORT_1_5_5_5_REV << 16,
		GL_BGRA | GL_UNSIGNED_INT_2_10_10_10_REV << 16,
		0, //GL_BGRA | GL_HALF_FLOAT << 16,
	};

	/**Loads an image encoded in a custom format.
	 * @param is image source
	 * @return image data: {S w, S h, US gl_format, US gl_type, (w * h * T) pixel data}
	 * allocated from {@link MemoryStack}.
	 * @throws IOException */
	public static ByteBuffer readImage(InputStream is, MemoryStack ms) throws IOException {
		int f = is.read(), w = is.read(), h = is.read();
		if ((f | w | h) < 0) throw new EOFException();
		int fmt = FORMATS[f & 31];
		if (fmt == 0) throw new IOException("unsupported color format: " + (f & 31));
		int e = f & 3, l = ++w * ++h << e;
		ByteBuffer img = ms.malloc(8 + l);
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

	private static ByteBuffer readRLE(InputStream in, ByteBuffer buf, int unit) throws IOException {
		byte[] arr = new byte[unit];
		while(buf.hasRemaining()) {
			int hdr = in.read();
			if (hdr < 0) throw new EOFException();
			if ((hdr & 128) != 0) {
				for (int i = 0; i < unit; i++)
					arr[i] = (byte)in.read();
				for (hdr &= 127; hdr >= 0; hdr--)
					buf.put(arr);
			} else for (hdr = (hdr + 1) * unit; hdr > 0; hdr--)
				buf.put((byte)in.read());
		}
		return buf;
	}

	public static ByteBuffer readTGA(InputStream in, MemoryStack ms) throws IOException {
		ByteBuffer hdr = ByteBuffer.wrap(in.readNBytes(18)).order(ByteOrder.LITTLE_ENDIAN);
		in.skipNBytes(hdr.get(0) & 0xff); //skip id field
		ByteBuffer map = hdr.get(1) == 0 ? null
			: ByteBuffer.wrap(in.readNBytes(((hdr.get(7) & 0xff) + 7 >> 3) * hdr.getChar(5)));
		short w = hdr.getShort(12), h = hdr.getShort(14);
		int type = hdr.get(2);
		if (type != 1 && type != 2 && type != 9 && type != 10)
			throw new IOException("unsupported TGA image type " + type);
		int depth = (hdr.get((type & 7) == 1 ? 7 : 16) & 0xff) + 7 >> 3;
		ByteBuffer img = ms.malloc(8 + w * h * depth);
		img.putShort(w).putShort(h).putInt(switch(depth) {
			case 2 -> GL_BGRA | GL_UNSIGNED_SHORT_1_5_5_5_REV << 16;
			case 3 -> GL_BGR | GL_UNSIGNED_BYTE << 16;
			case 4 -> GL_BGRA | GL_UNSIGNED_BYTE << 16;
			default -> throw new IOException("unsupported " + depth * 8 + " bit color depth");
		});
		int dataUnit = (hdr.get(16) & 0xff) + 7 >> 3;
		ByteBuffer data = img.position(img.capacity() - w * h * dataUnit).slice();
		((type & 8) != 0 ? readRLE(in, data, dataUnit) : data.put(in.readNBytes(data.remaining()))).flip();
		if ((hdr.get(17) & 32) == 0) {//vertical flip
			for (int y = (h >> 1) - 1; y >= 0; y--) {
				for (int s = w * dataUnit, p = y * s, q = (h - y - 1) * s; s > 0; s--, p++, q++) {
					byte b = data.get(p);
					data.put(p, data.get(q));
					data.put(q, b);
				}
			}
		}
		if ((type & 7) == 1) {
			if (dataUnit != 1 && dataUnit != 2)
				throw new IOException("unsupported index size " + dataUnit + " bytes");
			int ofs = hdr.getChar(3);
			for (int p = 8; data.hasRemaining();) {
				int i = dataUnit == 1 ? data.get() & 0xff : data.getShort() & 0xffff;
				img.put(p, map, (i - ofs) * depth, depth);
			}
		}
		return img.clear();
	}

	private static void write(OutputStream out, int bytes, int val) throws IOException {
		for (; bytes > 0; bytes--, val >>= 8)
			out.write(val);
	}

	public static void writeTGA16(OutputStream out, ByteBuffer data, int scan, int w, int h) throws IOException {
		out.write(0); //ID length
		out.write(0); //color palette
		out.write(2); //type: uncompressed RGB
		out.write(new byte[5]); //palette info
		write(out, 2, 0); //origin X
		write(out, 2, 0); //origin Y
		write(out, 2, w); //width
		write(out, 2, h); //height
		out.write(16); //16 bit color depth
		out.write(0b10_0001); //upper left origin
		byte[] arr = new byte[w * 2];
		for (int i = h, p = data.position(); i > 0; i--, p += scan) {
			data.position(p).get(arr);
			out.write(arr);
		}
	}

}
