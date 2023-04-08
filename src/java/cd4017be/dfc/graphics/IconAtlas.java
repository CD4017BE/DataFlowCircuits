package cd4017be.dfc.graphics;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static java.lang.Math.*;
import static org.lwjgl.opengl.GL20C.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import cd4017be.dfc.lang.CircuitFile;
import cd4017be.util.AtlasSprite;
import cd4017be.util.GLUtils;

/**
 * @author cd4017be */
public class IconAtlas {

	private final HashMap<URL, WeakReference<SpriteModel>> loaded = new HashMap<>();
	private final SpriteModel missing;
	private final BitSet usedIds = new BitSet();
	private AtlasSprite atlas;
	public final int levels, shader;
	private final int texScale;
	private int texId, idBuf;
	private boolean doResize;

	public IconAtlas(int shader, int levels, int w0, int h0, int n0) {
		this.atlas = new AtlasSprite(w0, h0);
		this.levels = levels;
		genIndexTexture(n0);
		genAtlasTexture(w0 << levels, h0 << levels);
		this.shader = shader;
		this.texScale = glGetUniformLocation(shader, "texScale");
		updateShader();
		glUniform1i(glGetUniformLocation(shader, "texture"), 0);
		glUniform1i(glGetUniformLocation(shader, "atlas"), 1);
		glBindTexture(GL_TEXTURE_1D, 0);
		glBindTexture(GL_TEXTURE_2D, 0);
		checkGLErrors();
		this.missing = load(IconAtlas.class.getResource("/textures/missing.tga"));
	}

	private void genIndexTexture(int n) {
		if (idBuf != 0) glDeleteTextures(idBuf);
		glBindTexture(GL_TEXTURE_1D, idBuf = glGenTextures());
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA16, n << 1, 0, GL_RGBA, GL_UNSIGNED_SHORT, MemoryUtil.NULL);
		checkGLErrors();
	}

	private void genAtlasTexture(int w, int h) {
		if (texId != 0) glDeleteTextures(texId);
		glBindTexture(GL_TEXTURE_2D, texId = glGenTextures());
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		//glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
		//glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, levels);
		//glTexParameteri(GL_TEXTURE_2D, GL_GENERATE_MIPMAP, GL_TRUE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, MemoryUtil.NULL);
		checkGLErrors();
	}

	private void updateShader() {
		glUseProgram(shader);
		glUniform3f(texScale,
			1F / (float)(glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) >> levels),
			1F / (float)(glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT) >> levels),
			1F / (float)(glGetTexLevelParameteri(GL_TEXTURE_1D, 0, GL_TEXTURE_WIDTH) >> 1)
		);
		checkGLErrors(1);
	}

	@Override
	protected void finalize() {
		glDeleteTextures(texId);
		glDeleteTextures(idBuf);
		texId = 0;
		idBuf = 0;
	}

	public void bind() {
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_1D, idBuf);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, texId);
		glUseProgram(shader);
	}

	public ArrayList<SpriteModel> getLoaded() {
		ArrayList<SpriteModel> list = new ArrayList<>(loaded.size());
		for (Iterator<WeakReference<SpriteModel>> it = loaded.values().iterator(); it.hasNext();) {
			SpriteModel m = it.next().get();
			if (m == null) it.remove();
			else list.add(m);
		}
		return list;
	}

	public SpriteModel get(URL path) {
		if (path == null) return missing;
		WeakReference<SpriteModel> r = loaded.get(path);
		SpriteModel m = r == null ? null : r.get();
		return m != null ? m : load(path);
	}

	private SpriteModel load(URL path) {
		try (MemoryStack ms = MemoryStack.stackPush()){
			byte[] data = CircuitFile.loadResource(path, 65536);
			SpriteModel m = new SpriteModel();
			m.readFromImageMetadata(data);
			load(GLUtils.readTGA(new ByteArrayInputStream(data), ms), m);
			loaded.put(path, new WeakReference<>(m));
			System.out.println("loaded icon " + path);
			return m;
		} catch(IOException e) {
			e.printStackTrace();
			return missing;
		}
	}

	/**@param level mip-map level to read
	 * @param format GL color format
	 * @param type pixel data type
	 * @param bpp bytes per pixel
	 * @return {short scanline, byte[bpp]... pixels}
	 * must be {@link MemoryUtil#memFree(java.nio.Buffer) freed} after use. */
	public ByteBuffer getData(int level, int format, int type, int bpp) {
		glBindTexture(GL_TEXTURE_2D, texId);
		int tw = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_WIDTH);
		int th = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_HEIGHT);
		ByteBuffer buf = MemoryUtil.memAlloc(tw * th * bpp + 2);
		buf.putShort((short)(tw * bpp));
		glGetTexImage(GL_TEXTURE_2D, level, format, type, buf);
		checkGLErrors();
		return buf.clear();
	}

	public AtlasSprite load(ByteBuffer img, SpriteModel model) {
		AtlasSprite old = model.icon, icon;
		glBindTexture(GL_TEXTURE_2D, texId);
		glBindTexture(GL_TEXTURE_1D, idBuf);
		int w = img.getShort(), h = img.getShort(), ws = w >> levels, hs = h >> levels;
		if (old != null && old.w >= ws && old.h >= hs) {
			icon = old;
			icon.w = ws;
			icon.h = hs;
		} else while((icon = atlas.place(ws, hs)) == null)
			enlargeAtlas();
		doResize = false;
		glTexSubImage2D(
			GL_TEXTURE_2D, 0, icon.x << levels, icon.y << levels, w, h,
			img.getChar(), img.getChar(), img
		);
		checkGLErrors();
		if (old == null) {
			int id = usedIds.nextClearBit(0);
			usedIds.set(icon.id = id);
			if (id >= glGetTexLevelParameteri(GL_TEXTURE_1D, 0, GL_TEXTURE_WIDTH) >> 1)
				enlargeIndices();
		} else icon.id = old.id;
		updateId(icon, model);
		checkGLErrors();
		glBindTexture(GL_TEXTURE_2D, 0);
		glBindTexture(GL_TEXTURE_1D, 0);
		return icon;
	}

	private void enlargeAtlas() {
		ByteBuffer old = getData(0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 4);
		try (MemoryStack ms = MemoryStack.stackPush()) {
			int s = old.getShort();
			int w = s >> 2, h = old.remaining() / s;
			if (doResize) {
				if (w > h) h <<= 1; else w <<= 1;
				System.out.printf("enlarging icon atlas to %d x %d\n", w, h);
				genAtlasTexture(w, h);
			} else {
				System.out.println("rearranging icon atlas");
				doResize = true;
			}
			ArrayList<SpriteModel> loaded = getLoaded();
			Collections.sort(loaded, (a, b)-> b.icon.A() - a.icon.A());
			ByteBuffer buf; {
				int max = 0;
				for (SpriteModel model : loaded) {
					AtlasSprite icon = model.icon;
					max = Math.max(max, icon.w * icon.h);
				}
				buf = ms.malloc(max << levels * 2 + 2);
			}
			usedIds.clear();
			atlas = new AtlasSprite(w >> levels, h >> levels);
			for (SpriteModel model : loaded) {
				AtlasSprite icon = model.icon;
				if (icon.A() == 0) {
					model.icon = AtlasSprite.NULL;
					continue;
				}
				AtlasSprite ns = atlas.place(icon.w, icon.h);
				for (
					int i = 0, di = icon.w << levels + 2, i1 = di * (icon.h << levels),
					j = (icon.y << levels) * s + (icon.x << levels + 2) + 2;
					i < i1; i += di, j += s
				) buf.put(i, old, j, di);
				glTexSubImage2D(GL_TEXTURE_2D, 0, ns.x << levels, ns.y << levels, ns.w << levels, ns.h << levels, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buf);
				checkGLErrors();
				usedIds.set(ns.id = icon.id);
				updateId(ns, model);
			}
		} finally {
			MemoryUtil.memFree(old);
		}
		updateShader();
		checkGLErrors();
	}

	private void enlargeIndices() {
		int n = glGetTexLevelParameteri(GL_TEXTURE_1D, 0, GL_TEXTURE_WIDTH);
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(n * 8);
			glGetTexImage(GL_TEXTURE_1D, 0, GL_RGBA, GL_UNSIGNED_SHORT, buf);
			genIndexTexture(n);
			glTexSubImage1D(GL_TEXTURE_1D, 0, 0, n, GL_RGBA, GL_UNSIGNED_SHORT, buf);
		}
		updateShader();
	}

	private short scale(int r, int l) {
		return (short)(l == 0 ? 0 : max(min((r << 16 - levels) / l, 65535), 0));
	}

	private void updateId(AtlasSprite icon, SpriteModel model) {
		model.icon = icon;
		int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) >> levels;
		int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT) >> levels;
		glTexSubImage1D(GL_TEXTURE_1D, 0, icon.id * 2, 2, GL_RGBA, GL_UNSIGNED_SHORT, new short[] {
			(short)((icon.x << 16) / w    ), (short)((icon.y << 16) / h    ),
			(short)((icon.w << 16) / w - 1), (short)((icon.h << 16) / h - 1),
			scale(model.rx0, icon.w), scale(model.ry0, icon.h),
			scale(model.rx1, icon.w), scale(model.ry1, icon.h),
		});
	}

	public interface IconHolder {
		AtlasSprite icon();
		float[] icon(AtlasSprite icon);
	}

}
