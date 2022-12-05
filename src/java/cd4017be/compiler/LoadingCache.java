package cd4017be.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.editor.Shaders;
import cd4017be.util.GLUtils;
import cd4017be.util.IconAtlas;

/**
 * 
 * @author CD4017BE */
public class LoadingCache {

	public final HashMap<Type, Type> types = new HashMap<>();
	private final HashMap<Path, Module> modules = new HashMap<>();
	public final IconAtlas icons;
	public final BlockModel defaultModel;
	public final BlockDef placeholder;

	public LoadingCache(boolean graphics) {
		if (graphics) {
			this.icons = new IconAtlas(Shaders.blockP, 2, 32, 32, 256);
			this.defaultModel = new BlockModel(null, "default");
			try (MemoryStack ms = MemoryStack.stackPush()) {
				InputStream is = getClass().getResourceAsStream("/textures/placeholder.tga");
				if (is == null) throw new IOException("missing placeholder icon");
				icons.load(GLUtils.readTGA(is, ms), defaultModel);
			} catch(IOException e) {
				e.printStackTrace();
			}
		} else {
			this.icons = null;
			this.defaultModel = null;
		}
		placeholder = new BlockDef(null, "missing", null, new String[0], new String[0], new String[0], defaultModel, 0);
	}

	public Module getModule(Path path) {
		return modules.computeIfAbsent(path, p -> new Module(this, p));
	}

}
