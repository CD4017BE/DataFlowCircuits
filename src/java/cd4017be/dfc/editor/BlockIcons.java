package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static org.lwjgl.glfw.GLFW.glfwExtensionSupported;
import static org.lwjgl.opengl.ARBCopyImage.glCopyImageSubData;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import cd4017be.dfc.lang.BlockDef;
import cd4017be.util.AtlasSprite;
import cd4017be.util.GLUtils;

/**
 * @author CD4017BE */
public class BlockIcons {

	private static final boolean CAN_ENLARGE = glfwExtensionSupported("GL_ARB_copy_image");
	private static final int SCALE = 2;
	public final HashMap<String, BlockDef> defs;
	private final ArrayList<BlockDef> loaded;
	private AtlasSprite atlas;
	private int texId;
	public float scaleX, scaleY;
	private boolean update;

	public BlockIcons(HashMap<String, BlockDef> defs) {
		this.defs = defs;
		this.loaded = new ArrayList<>();
		int size = CAN_ENLARGE ? 64 : 1024;
		init(size, size);
		load("traces", null);
	}

	private void init(int w, int h) {
		glBindTexture(GL_TEXTURE_2D, texId = glGenTextures());
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB5_A1, w, h, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, MemoryUtil.NULL);
		checkGLErrors();
		atlas = new AtlasSprite(w >>= SCALE, h >>= SCALE);
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
	}

	public BlockDef get(String name) {
		BlockDef def = defs.get(name);
		if (def != null && def.icon == null) {
			def.icon = load("blocks/" + name, def);
			loaded.add(def);
		}
		return def;
	}

	private AtlasSprite load(String path, BlockDef def) {
		System.out.println("loading icon: " + path);
		AtlasSprite icon;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer img;
			try (InputStream is = BlockIcons.class.getResourceAsStream("/textures/" + path + ".im")) {
				if (is == null) throw new FileNotFoundException(path);
				img = GLUtils.readImage(is);
				if (def != null) def.readLayout(is);
			} catch(IOException e) {
				e.printStackTrace();
				return AtlasSprite.NULL;
			}
			bind();
			int w = img.getShort(), h = img.getShort();
			while((icon = atlas.place(w >> SCALE, h >> SCALE)) == null) enlarge();
			glTexSubImage2D(
				GL_TEXTURE_2D, 0, icon.x << SCALE, icon.y << SCALE, w, h,
				img.getChar(), img.getChar(), img
			);
		}
		checkGLErrors();
		return icon;
	}

	private void enlarge() {
		int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
		int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
		if (w > h) h <<= 1; else w <<= 1;
		System.out.printf("enlarging icon atlas to %d x %d\n", w, h);
		int old = texId;
		AtlasSprite traces = atlas;
		init(w, h);
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
		return ns;
	}

	@Override
	protected void finalize() {
		glDeleteTextures(texId);
	}

}
