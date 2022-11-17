package cd4017be.dfc.editor;

import static org.lwjgl.opengl.GL20C.*;

import java.io.*;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;
import cd4017be.dfc.lang.*;
import cd4017be.util.*;

/**
 * @author CD4017BE */
public class BlockIcons extends IconAtlas {

	public File root;
	public final BlockDef placeholder;

	public BlockIcons() {
		super(Shaders.blockP, 2, 16, 16, 256);
		load("placeholder", placeholder = new BlockDef("", 0, 0, false));
		placeholder.shortDesc = placeholder.longDesc = placeholder.name;
	}

	/**@return {short scanline, short... pixels} */
	public ByteBuffer getData(MemoryStack ms) {
		return super.getData(ms, 0, GL_BGRA, GL_UNSIGNED_SHORT_1_5_5_5_REV, 2);
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

}
