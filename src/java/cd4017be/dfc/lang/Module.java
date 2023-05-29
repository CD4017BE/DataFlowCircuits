package cd4017be.dfc.lang;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.lang.builders.ConstList;
import cd4017be.dfc.lang.instructions.IntrinsicLoader;
import cd4017be.util.*;
import modules.dfc.module.Intrinsics;

/**
 * 
 * @author CD4017BE */
public class Module {

	public final String name;
	public final Path path;
	public final LinkedHashMap<String, Module> imports = new LinkedHashMap<>();
	private final HashMap<String, BlockDef> blocks = new LinkedHashMap<>();
	private final HashMap<String, Type> types = new HashMap<>();
	public final HashMap<String, PaletteGroup> palettes = new HashMap<>();
	public final HashMap<String, Function<BlockDef, NodeAssembler>> assemblers = new HashMap<>();
	public final HashMap<String, ArgumentParser> parsers = new HashMap<>();
	final Class<?> moduleImpl;
	private final ConstList cfg;
	private boolean loaded;
	int trace0 = -3;

	public Module(String name, Path path, ClassLoader cl) {
		this.name = name;
		this.path = path;
		Class<?> impl = null;
		if (cl != null) try {
			impl = cl.loadClass("modules." + name.replace('/', '.') + ".Intrinsics");
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
		Path path = this.path.resolve("traces.tga");
		if (Files.exists(path)) try (
			InputStream is = Files.newInputStream(path);
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

	public Path icon(String name) {
		return path.resolve("icons/" + name + ".tga");
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
