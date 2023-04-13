package cd4017be.dfc.lang;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import cd4017be.dfc.editor.Shaders;
import cd4017be.dfc.graphics.IconAtlas;
import cd4017be.dfc.modules.core.Intrinsics;
import cd4017be.util.TraceAtlas;

/**
 * 
 * @author CD4017BE */
public class LoadingCache {

	private static final HashMap<String, WeakReference<Module>> MODULES = new HashMap<>();
	public static final Module CORE;
	public static final BlockDef MISSING_BLOCK, IN_BLOCK, OUT_BLOCK;
	static {
		CORE = new Module("core", Intrinsics.class);
		MODULES.put(CORE.name, new WeakReference<Module>(CORE));
		MISSING_BLOCK = CORE.getBlock("missing");
		IN_BLOCK = CORE.getBlock("in");
		OUT_BLOCK = CORE.getBlock("out");
	}

	public static IconAtlas ATLAS;
	public static TraceAtlas TRACES;

	public static void initGraphics() {
		if (ATLAS == null)
			ATLAS = new IconAtlas(Shaders.blockP, 2, 16, 16, 256);
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

	public static synchronized Module getModule(String path) {
		var ref = MODULES.get(path);
		Module m = ref == null ? null : ref.get();
		if (m == null) {
			MODULES.put(path, new WeakReference<>(m = new Module(path, null)));
		}
		return m;
	}

}
