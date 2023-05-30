package cd4017be.dfc.lang;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Function;

import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.lang.LoadingCache.ModuleRoot;
import cd4017be.dfc.lang.builders.ConstList;
import cd4017be.dfc.lang.instructions.IntrinsicLoader;
import cd4017be.util.*;
import modules.dfc.module.Intrinsics;

/**
 * 
 * @author CD4017BE */
public class Module {

	public final String name;
	private final ModuleRoot root;
	public final LinkedHashMap<String, Module> imports = new LinkedHashMap<>();
	private final HashMap<String, BlockDef> blocks = new LinkedHashMap<>();
	private final HashMap<String, Type> types = new HashMap<>();
	public final HashMap<String, PaletteGroup> palettes = new HashMap<>();
	public final HashMap<String, Function<BlockDef, NodeAssembler>> assemblers = new HashMap<>();
	public final HashMap<String, ArgumentParser> parsers = new HashMap<>();
	final Class<?> moduleImpl;
	private final ConstList cfg;
	private String[] iconIndex;
	private boolean loaded;
	int trace0 = -3;

	public Module(String name, ModuleRoot root) {
		this.name = name;
		this.root = root;
		Class<?> impl = null;
		try {
			impl = root.cl.loadClass("modules." + name.replace('/', '.') + ".Intrinsics");
			System.out.println("loaded intrinsics for module " + name);
		} catch (ClassNotFoundException e) {
		} catch (LinkageError e) {
			e.printStackTrace();
		}
		this.moduleImpl = impl;
		this.cfg = getBlock("").defineModule();
		IntrinsicLoader.init(this, impl);
		loadTraces();
	}

	public void loadTraces() {
		if (Main.TRACES == null || trace0 >= -2) return;
		trace0 = 0;
		try (
			InputStream is = url("traces.tga").openStream();
			MemoryStack ms = MemoryStack.stackPush();
		) {
			trace0 = Main.TRACES.load(GLUtils.readTGA(is, ms), this) - 2;
			System.out.println("loaded traces for module " + name);
		} catch (IOException e) {
			System.err.printf("can't load traces for module %s\n because %s\n", name, e);
		}
	}

	public synchronized void reload() {
		loaded = false;
	}

	public Module loadPalettes() {
		if (!loaded) {
			Intrinsics.loadModule(this);
			loaded = true;
		}
		return this;
	}

	public HashMap<String, Value> data() {
		return cfg.signals();
	}

	public BlockDef getBlock(String name) {
		return blocks.computeIfAbsent(name, n -> new BlockDef(this, n));
	}

	public Type getType(String name) {
		return types.computeIfAbsent(name, n -> new Type(this, n));
	}

	public URL url(String path) throws MalformedURLException {
		return new URL(root.path, name + '/' + path);
	}

	public URL icon(String name) {
		try {
			return url("icons/" + name + ".tga");
		} catch(MalformedURLException e) {
			return null;
		}
	}

	public void listIcons(ArrayList<String> list) {
		if (iconIndex == null) try {
				iconIndex = CircuitFile.getIndex(url("icons/"), (file, rl, index) -> {
					if (!file.getName().endsWith(".tga")) return false;
					String name = file.getPath();
					index.add(name.substring(rl, name.length() - 4).replace(File.separatorChar, '/'));
					return false;
				}, root.doIndex);
			} catch(MalformedURLException e) {
				e.printStackTrace();
				iconIndex = new String[0];
			}
		for (String s : iconIndex)
			list.add(s);
	}

	@Override
	public String toString() {
		return name;
	}

	public static class PaletteGroup {
		public final Module module;
		public final String name;
		public final BlockDef[] blocks;

		public PaletteGroup(Module module, String name, BlockDef[] blocks) {
			this.module = module;
			this.name = name;
			this.blocks = blocks;
			module.palettes.put(name, this);
		}

	}

}
