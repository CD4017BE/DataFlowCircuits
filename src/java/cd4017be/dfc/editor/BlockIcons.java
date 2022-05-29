package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static org.lwjgl.glfw.GLFW.glfwExtensionSupported;
import static org.lwjgl.opengl.ARBCopyImage.glCopyImageSubData;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_BGRA;
import static org.lwjgl.opengl.GL12C.GL_UNSIGNED_SHORT_1_5_5_5_REV;
import static org.lwjgl.opengl.GL31C.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import cd4017be.dfc.lang.*;
import cd4017be.util.*;

/**
 * @author CD4017BE */
public class BlockIcons {

	private static final boolean CAN_ENLARGE = glfwExtensionSupported("GL_ARB_copy_image");
	private static final int SCALE = 2;
	private final ArrayList<BlockDef> loaded;
	private AtlasSprite atlas;
	private int texId, idBuf;
	public float scaleX, scaleY;
	private boolean update;
	public File root;
	public final BlockDef placeholder;

	public BlockIcons() {
		this.loaded = new ArrayList<>();
		glBindBuffer(GL_TEXTURE_BUFFER, idBuf = glGenBuffers());
		glBufferData(GL_TEXTURE_BUFFER, 1024, GL_STATIC_DRAW);
		checkGLErrors();
		int size = CAN_ENLARGE ? 16 : 256;
		init(size, size);
		load("traces", null);
		load("placeholder", placeholder = new BlockDef("", 0, 0, false));
		placeholder.shortDesc = placeholder.longDesc = placeholder.name;
	}

	private void init(int w, int h) {
		glBindTexture(GL_TEXTURE_2D, texId = glGenTextures());
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB5_A1, w << SCALE, h << SCALE, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, MemoryUtil.NULL);
		checkGLErrors();
		atlas = new AtlasSprite(w, h);
		scaleX = 1F / (float)w; scaleY = 1F / (float)h;
		update = true;
	}

	public boolean update() {
		if (!update) return false;
		glUseProgram(Shaders.blockP);
		glUniform2f(Shaders.block_gridScale, scaleX, scaleY);
		checkGLErrors();
		update = false;
		return true;
	}

	public void bind() {
		glBindTexture(GL_TEXTURE_2D, texId);
		glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA8UI, idBuf);
	}

	/**@return {short scanline, short... pixels} */
	public ByteBuffer getData(MemoryStack ms) {
		glBindTexture(GL_TEXTURE_2D, texId);
		int tw = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
		int th = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
		ByteBuffer buf = ms.malloc(tw * th * 2 + 2);
		buf.putShort((short)(tw * 2));
		glGetTexImage(GL_TEXTURE_2D, 0, GL_BGRA, GL_UNSIGNED_SHORT_1_5_5_5_REV, buf);
		return buf.clear();
	}

	public void load(BlockDef def, BlockRegistry reg) {
		if (def.icon != null) return;
		System.out.println("loading icon: " + def.name);
		try (CircuitFile file = reg.openFile(def.name, false)) {
			load(file.readIcon(), def);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private AtlasSprite load(String path, BlockDef def) {
		path = "/textures/" + path + ".tga";
		try (
			InputStream is = BlockIcons.class.getResourceAsStream(path);
			MemoryStack ms = MemoryStack.stackPush()
		) {
			if (is == null) throw new FileNotFoundException(path);
			return load(GLUtils.readTGA(is, ms), def);
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public AtlasSprite load(InputStream is, BlockDef def) throws IOException {
		if (is == null) return def.icon = placeholder.icon;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			return load(GLUtils.readImage(is, ms), def);
		}
	}

	public AtlasSprite load(ByteBuffer img, BlockDef def) throws IOException {
		AtlasSprite icon = AtlasSprite.NULL;
		glBindTexture(GL_TEXTURE_2D, texId);
		glBindBuffer(GL_TEXTURE_BUFFER, idBuf);
		int w = img.getShort(), h = img.getShort(), ws = w >> SCALE, hs = h >> SCALE;
		if (def != null && def.icon != null && def.icon.w >= ws && def.icon.h >= hs) {
			icon = def.icon;
			icon.w = ws;
			icon.h = hs;
		} else while((icon = atlas.place(ws, hs)) == null)
			enlarge();
		glTexSubImage2D(
			GL_TEXTURE_2D, 0, icon.x << SCALE, icon.y << SCALE, w, h,
			img.getChar(), img.getChar(), img
		);
		checkGLErrors();
		if (def == null) setIndex(icon, -1);
		else if (def.icon == null) {
			setIndex(def.icon = icon, loaded.size());
			loaded.add(def);
		} else {
			setIndex(icon, def.icon.id);
			def.icon = icon;
		}
		return icon;
	}

	private void enlarge() {
		int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
		int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
		if (w > h) h <<= 1; else w <<= 1;
		System.out.printf("enlarging icon atlas to %d x %d\n", w, h);
		int old = texId;
		AtlasSprite traces = atlas;
		init(w >> SCALE, h >> SCALE);
		copy(traces, old);
		Collections.sort(loaded, (a, b)-> b.icon.A() - a.icon.A());
		for (BlockDef def : loaded) def.icon = copy(def.icon, old);
		glDeleteTextures(old);
		checkGLErrors();
		if (!CAN_ENLARGE) System.err.println("Missing GL_ARB_copy_image extension. Update your OpenGL drivers!");
	}

	private AtlasSprite copy(AtlasSprite s, int old) {
		if (s.A() == 0) return AtlasSprite.NULL;
		AtlasSprite ns = atlas.place(s.w, s.h);
		glCopyImageSubData(
			old, GL_TEXTURE_2D, 0, s.x << SCALE, s.y << SCALE, 0,
			texId, GL_TEXTURE_2D, 0, ns.x << SCALE, ns.y << SCALE, 0,
			s.w << SCALE, s.h << SCALE, 1
		);
		checkGLErrors();
		setIndex(ns, s.id);
		return ns;
	}

	private void setIndex(AtlasSprite s, int id) {
		if ((s.id = id) < 0) return;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			glBufferSubData(GL_TEXTURE_BUFFER, id * 4, ms.bytes(
				(byte)s.x, (byte)s.y, (byte)(s.x + s.w - 1), (byte)(s.y + s.h - 1)
			));
		}
		checkGLErrors();
	}

	@Override
	protected void finalize() {
		glDeleteTextures(texId);
	}

}
