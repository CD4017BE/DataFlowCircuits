package cd4017be.dfc.lang;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.*;

import cd4017be.dfc.lang.CircuitFile.Indexer;

/**
 * 
 * @author CD4017BE */
public class LoadingCache {

	private static final ArrayList<ModuleRoot> ROOTS = new ArrayList<>();
	private static final URL BUILTIN_ROOT;
	private static final HashMap<String, WeakReference<Module>> MODULES = new HashMap<>();
	public static final Module LOADER;
	static {
		BUILTIN_ROOT = LoadingCache.class.getResource("/modules/");
		ROOTS.add(new ModuleRoot(BUILTIN_ROOT, LoadingCache.class.getClassLoader(), true));
		LOADER = getModule("dfc/module");
	}

	public static void addRootPath(URL path, boolean index) {
		ROOTS.add(new ModuleRoot(path, new PluginClassLoader(path), index));
	}

	public static synchronized Module getModule(String name) {
		var ref = MODULES.get(name);
		Module m = ref == null ? null : ref.get();
		if (m == null) {
			if (ROOTS.isEmpty()) throw new IllegalStateException("no roots");
			ModuleRoot root = null;
			for (int i = 0; i < ROOTS.size(); i++)
				if ((root = ROOTS.get(i)).contains(name))
					break;
			m = new Module(name, root);
			MODULES.put(name, new WeakReference<>(m));
		}
		return m;
	}

	public static void listAllModules(ArrayList<String> list) {
		for (ModuleRoot root : ROOTS)
			for (String name : root.index)
				list.add(name);
	}

	public static class ModuleRoot implements Indexer {
		public final URL path;
		public final ClassLoader cl;
		public final boolean doIndex;
		public String[] index;

		public ModuleRoot(URL path, ClassLoader cl, boolean index) {
			this.path = path;
			this.cl = cl;
			this.doIndex = index;
			this.index = CircuitFile.getIndex(path, this, index);
		}

		public boolean contains(String name) {
			return Arrays.binarySearch(index, name) >= 0;
		}

		@Override
		public boolean visit(File file, int rootLen, ArrayList<String> index) {
			switch(file.getName()) {
			default: return false;
			case "module.dfc", "module.ds", "Intrinsics.class", "Intrinsics.java":
				index.add(file.getParent().substring(rootLen).replace(File.separatorChar, '/'));
				return true;
			}
		}
	}

}
