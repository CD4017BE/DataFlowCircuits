package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static org.lwjgl.glfw.GLFW.glfwExtensionSupported;
import static org.lwjgl.opengl.ARBCopyImage.glCopyImageSubData;
import static org.lwjgl.opengl.GL31C.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.DataFormatException;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.CircuitFile;
import cd4017be.util.*;

/**
 * @author CD4017BE */
public class BlockIcons {

	private static final boolean CAN_ENLARGE = glfwExtensionSupported("GL_ARB_copy_image");
	private static final int SCALE = 2;
	public final HashMap<String, BlockDef> defs;
	private final ArrayList<BlockDef> loaded, missing;
	private AtlasSprite atlas;
	private int texId, idBuf;
	public float scaleX, scaleY;
	private boolean update;
	public File root;

	public BlockIcons(HashMap<String, BlockDef> defs) {
		this.defs = defs;
		this.loaded = new ArrayList<>();
		this.missing = new ArrayList<>();
		glBindBuffer(GL_TEXTURE_BUFFER, idBuf = glGenBuffers());
		glBufferData(GL_TEXTURE_BUFFER, 1024, GL_STATIC_DRAW);
		checkGLErrors();
		int size = CAN_ENLARGE ? 16 : 256;
		init(size, size);
		load("../traces", null);
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

	private InputStream open(String name, boolean im) throws FileNotFoundException {
		if (name.startsWith("/"))
			return new FileInputStream(new File(root, name + (im ? ".im" : ".dfc")));
		InputStream is = BlockIcons.class.getResourceAsStream(
			im ? name.startsWith("../") ? "/textures/" + name.substring(3) + ".im"
				: "/textures/blocks/" + name + ".im"
				: "/circuits/" + name + ".dfc"
		);
		if (is == null) throw new FileNotFoundException(name);
		return is;
	}

	public boolean update() {
		if (!update) return false;
		glUseProgram(Shaders.blockP);
		glUniform2f(Shaders.block_gridScale, scaleX, scaleY);
		checkGLErrors();
		while (!missing.isEmpty()) {
			BlockDef def = missing.remove(missing.size() - 1);
			try (ExtInputStream in = new ExtInputStream(open(def.name, false))) {
				def.eval = new CircuitFile(in, this::get);
			} catch(IOException | DataFormatException e) {
				e.printStackTrace();
			}
		}
		update = false;
		return true;
	}

	public void bind() {
		glBindTexture(GL_TEXTURE_2D, texId);
		glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA8UI, idBuf);
	}

	public BlockDef get(String name) {
		BlockDef def = defs.computeIfAbsent(name, name1 -> {
			BlockDef def1 = new BlockDef(name1);
			missing.add(def1);
			update = true;
			return def1;
		});
		if (def.icon == null) load(name, def);
		return def;
	}

	public AtlasSprite load(String path, BlockDef def) {
		System.out.println("loading icon: " + path);
		AtlasSprite icon = AtlasSprite.NULL;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer img;
			try (InputStream is = open(path, true)) {
				img = GLUtils.readImage(is);
				if (def != null) def.readLayout(is);
			} catch(IOException e) {
				e.printStackTrace();
				return icon;
			}
			glBindTexture(GL_TEXTURE_2D, texId);
			glBindBuffer(GL_TEXTURE_BUFFER, idBuf);
			int w = img.getShort(), h = img.getShort();
			while((icon = atlas.place(w >> SCALE, h >> SCALE)) == null) enlarge();
			glTexSubImage2D(
				GL_TEXTURE_2D, 0, icon.x << SCALE, icon.y << SCALE, w, h,
				img.getChar(), img.getChar(), img
			);
			checkGLErrors();
			setIndex(icon, def != null ? loaded.size() : -1);
			return icon;
		} finally {
			if (def != null) {
				def.icon = icon;
				loaded.add(def);
			}
		}
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
