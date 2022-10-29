package cd4017be.util;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static org.lwjgl.opengl.GL14.GL_GENERATE_MIPMAP;
import static org.lwjgl.opengl.GL20C.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * @author cd4017be */
public class IconAtlas {

	private final ArrayList<IconHolder> loaded;
	private AtlasSprite atlas;
	public final int levels, shader;
	private final int texScale, texId, idBuf;

	public IconAtlas(int shader, int levels, int w0, int h0, int n0) {
		this.loaded = new ArrayList<>();
		this.atlas = new AtlasSprite(w0, h0);
		this.levels = levels;
		glBindTexture(GL_TEXTURE_1D, this.idBuf = glGenTextures());
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA16, n0, 0, GL_RGBA, GL_UNSIGNED_SHORT, MemoryUtil.NULL);
		checkGLErrors();
		glBindTexture(GL_TEXTURE_2D, this.texId = glGenTextures());
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, levels);
		glTexParameteri(GL_TEXTURE_2D, GL_GENERATE_MIPMAP, GL_TRUE);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w0 << levels, h0 << levels, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, MemoryUtil.NULL);
		checkGLErrors();
		this.shader = shader;
		this.texScale = glGetUniformLocation(shader, "texScale");
		updateShader();
		glUniform1i(glGetUniformLocation(shader, "texture"), 0);
		glUniform1i(glGetUniformLocation(shader, "atlas"), 1);
		glBindTexture(GL_TEXTURE_1D, 0);
		glBindTexture(GL_TEXTURE_2D, 0);
		checkGLErrors();
	}

	private void updateShader() {
		glUseProgram(shader);
		glUniform3f(texScale,
			1F / (float)(glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) >> levels),
			1F / (float)(glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT) >> levels),
			1F / (float)(glGetTexLevelParameteri(GL_TEXTURE_1D, 0, GL_TEXTURE_WIDTH) >> levels)
		);
		checkGLErrors(1);
	}

	@Override
	protected void finalize() {
		glDeleteTextures(texId);
		glDeleteTextures(idBuf);
	}

	public void bind() {
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_1D, idBuf);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, texId);
		glUseProgram(shader);
	}

	/**@return {short scanline, byte[bpp]... pixels} */
	public ByteBuffer getData(MemoryStack ms, int level, int format, int type, int bpp) {
		glBindTexture(GL_TEXTURE_2D, texId);
		int tw = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_WIDTH);
		int th = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_HEIGHT);
		ByteBuffer buf = ms.malloc(tw * th * bpp + 2);
		buf.putShort((short)(tw * bpp));
		glGetTexImage(GL_TEXTURE_2D, level, format, type, buf);
		glBindTexture(GL_TEXTURE_2D, 0);
		return buf.clear();
	}

	public AtlasSprite load(ByteBuffer img, IconHolder holder) {
		AtlasSprite old = holder.icon(), icon;
		glBindTexture(GL_TEXTURE_2D, texId);
		glBindTexture(GL_TEXTURE_1D, idBuf);
		int w = img.getShort(), h = img.getShort(), ws = w >> levels, hs = h >> levels;
		if (old != null && old.w >= ws && old.h >= hs) {
			icon = old;
			icon.w = ws;
			icon.h = hs;
		} else while((icon = atlas.place(ws, hs)) == null)
			enlargeAtlas();
		glTexSubImage2D(
			GL_TEXTURE_2D, 0, icon.x << levels, icon.y << levels, w, h,
			img.getChar(), img.getChar(), img
		);
		checkGLErrors();
		if (old == null) {
			if ((icon.id = loaded.size()) >= glGetTexLevelParameteri(GL_TEXTURE_1D, 0, GL_TEXTURE_WIDTH))
				enlargeIndices();
			loaded.add(holder);
		} else icon.id = old.id;
		updateId(icon);
		checkGLErrors();
		holder.icon(icon);
		glBindTexture(GL_TEXTURE_2D, 0);
		glBindTexture(GL_TEXTURE_1D, 0);
		return icon;
	}

	private void enlargeAtlas() {
		try (MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer old = getData(ms, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 4), buf;
			int s = old.getShort();
			int w = s >> 1, h = old.remaining() / s;
			if (w > h) h <<= 1; else w <<= 1;
			System.out.printf("enlarging icon atlas to %d x %d\n", w, h);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, MemoryUtil.NULL);
			Collections.sort(loaded, (a, b)-> b.icon().A() - a.icon().A());
			{
				int max = 0;
				for (IconHolder holder : loaded) {
					AtlasSprite icon = holder.icon();
					max = Math.max(max, icon.w * icon.h);
				}
				buf = ms.malloc(max << levels * 2 + 2);
			}
			atlas = new AtlasSprite(w >> levels, h >> levels);
			for (IconHolder holder : loaded) {
				AtlasSprite icon = holder.icon();
				if (icon.A() == 0) {
					holder.icon(AtlasSprite.NULL);
					continue;
				}
				AtlasSprite ns = atlas.place(icon.w, icon.h);
				for (
					int i = 0, di = ns.w << levels + 2, i1 = di * (ns.h << levels),
					j = (icon.y << levels) * s + (icon.x << levels + 2) + 2;
					i < i1; i += di, j += s
				) buf.put(i, old, j, di);
				glTexSubImage2D(GL_TEXTURE_2D, 0, ns.x << levels, ns.y << levels, ns.w << levels, ns.h << levels, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buf);
				checkGLErrors();
				updateId(ns);
				holder.icon(ns);
			}
		}
		checkGLErrors();
	}

	private void enlargeIndices() {
		int n = glGetTexLevelParameteri(GL_TEXTURE_1D, 0, GL_TEXTURE_WIDTH);
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(n * 8);
			glGetTexImage(GL_TEXTURE_1D, 0, GL_RGBA, GL_UNSIGNED_SHORT, buf);
			glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA16, n << 1, 0, GL_RGBA, GL_UNSIGNED_SHORT, MemoryUtil.NULL);
			glTexSubImage1D(GL_TEXTURE_1D, 0, 0, n, GL_RGBA, GL_UNSIGNED_SHORT, buf);
		}
		updateShader();
	}

	private void updateId(AtlasSprite icon) {
		int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) >> levels;
		int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT) >> levels;
		glTexSubImage1D(GL_TEXTURE_1D, 0, icon.id, 1, GL_RGBA, GL_UNSIGNED_SHORT, new short[] {
			(short)((icon.x << 16) / w), (short)((icon.y << 16) / h),
			(short)((icon.w - 1 << 16) / w), (short)((icon.h - 1 << 16) / h)
		});
	}

	public interface IconHolder {
		AtlasSprite icon();
		void icon(AtlasSprite icon);
	}

}
