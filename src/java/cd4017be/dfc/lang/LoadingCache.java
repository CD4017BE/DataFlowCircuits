package cd4017be.dfc.lang;

import static java.io.File.separatorChar;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;

/**
 * 
 * @author CD4017BE */
public class LoadingCache {

	private static final ArrayList<ModuleRoot> ROOTS = new ArrayList<>();
	private static final Path BUILTIN_ROOT;
	private static final HashMap<String, WeakReference<Module>> MODULES = new HashMap<>();
	public static final Module LOADER;
	static {
		try {
			BUILTIN_ROOT = Path.of(LoadingCache.class.getResource("/modules/").toURI());
		} catch(URISyntaxException e) {
			throw new RuntimeException(e);
		}
		ROOTS.add(new ModuleRoot(BUILTIN_ROOT, LoadingCache.class.getClassLoader()));
		LOADER = getModule("dfc/module");
	}

	public static void addRootPath(Path path) {
		ROOTS.add(new ModuleRoot(path, new PluginClassLoader(path)));
	}

	public static synchronized Module getModule(String name) {
		var ref = MODULES.get(name);
		Module m = ref == null ? null : ref.get();
		if (m == null) {
			Path path = null;
			ClassLoader cl = null;
			for (ModuleRoot root : ROOTS) {
				path = root.path.resolve(name);
				if (Files.exists(path)) {
					cl = root.cl;
					break;
				}
			}
			m = new Module(name, path, cl);
			MODULES.put(name, new WeakReference<>(m));
		}
		return m;
	}

	public static void listAllModules(ArrayList<String> list) {
		ArrayList<Path> dirs = new ArrayList<>();
		for (ModuleRoot root : ROOTS) {
			dirs.add(root.path);
			for (int p; (p = dirs.size() - 1) >= 0;) {
				Path dir = dirs.remove(p);
				try {
					for (Iterator<Path> it = Files.list(dir).iterator(); it.hasNext();) {
						Path path = it.next();
						if (Files.isDirectory(path)) {
							dirs.add(path);
							continue;
						}
						String name = path.getFileName().toString();
						if (name.startsWith("module.") || name.startsWith("Intrinsics.")) {
							name = root.path.relativize(dir).normalize().toString();
							list.add(name.replace(separatorChar, '/'));
							dirs.subList(p, dirs.size()).clear();
							break;
						}
					}
				} catch(IOException e) {}
			}
		}
	}

	public static record ModuleRoot(Path path, ClassLoader cl) {}

}
