package cd4017be.compiler;

import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.WeakHashMap;

import cd4017be.dfc.editor.Shaders;
import cd4017be.util.IconAtlas;
import cd4017be.util.TraceAtlas;

/**
 * 
 * @author CD4017BE */
public class LoadingCache {

	private static final WeakHashMap<Path, WeakReference<Module>> MODULES = new WeakHashMap<>();
	public static final Module CORE;
	public static final BlockModel MISSING_MODEL;
	public static final BlockDef MISSING_BLOCK, IN_BLOCK, OUT_BLOCK;
	static {
		try {
			CORE = getModule(Path.of(LoadingCache.class.getResource("/core/module.cfg").toURI()).getParent());
		} catch(URISyntaxException e) {
			throw new RuntimeException(e);
		}
		MISSING_MODEL = CORE.models.get("missing");
		MISSING_BLOCK = CORE.getBlock("missing");
		IN_BLOCK = CORE.getBlock("in");
		OUT_BLOCK = CORE.getBlock("out");
	}

	public static IconAtlas ATLAS;
	public static TraceAtlas TRACES;

	public static void initGraphics() {
		if (ATLAS == null)
			ATLAS = new IconAtlas(Shaders.blockP, 2, 32, 32, 256);
		if (TRACES == null) {
			TRACES = new TraceAtlas(Shaders.traceP, 8, 256);
			CORE.loadTraces();
		}
	}

//	public LoadingCache(boolean graphics) {
//		if (graphics) {
//			
//			this.defaultModel = new BlockModel(null, "default");
//			try (MemoryStack ms = MemoryStack.stackPush()) {
//				InputStream is = getClass().getResourceAsStream("/textures/placeholder.tga");
//				if (is == null) throw new IOException("missing placeholder icon");
//				icons.load(GLUtils.readTGA(is, ms), defaultModel);
//			} catch(IOException e) {
//				e.printStackTrace();
//			}
//		} else {
//			this.icons = null;
//		}
//	}

	public static Module getModule(Path path) {
		var ref = MODULES.get(path);
		Module m = ref == null ? null : ref.get();
		if (m == null)
			MODULES.put(path, new WeakReference<>(m = new Module(path)));
		return m;
	}

}
