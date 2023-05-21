package cd4017be.dfc.lang;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.lang.instructions.IntrinsicLoader;
import cd4017be.util.*;
import modules.loader.Intrinsics;

/**
 * 
 * @author CD4017BE */
public class Module {

	public final String name;
	public final Path path;
	public final LinkedHashMap<String, Module> imports = new LinkedHashMap<>();
	public final HashMap<Module, String> modNames = new HashMap<>();
	public final HashMap<String, BlockDef> blocks = new LinkedHashMap<>();
	public final HashMap<String, Type> types = new HashMap<>();
	public final HashMap<String, Function<BlockDef, NodeAssembler>> assemblers = new HashMap<>();
	public final HashMap<String, ArgumentParser> parsers = new HashMap<>();
	private final Class<?> moduleImpl;
	private boolean loaded;
	int trace0 = -1;

	public Module(String name, Path path, ClassLoader cl) {
		this.name = name;
		this.path = path;
		Class<?> impl = null;
		if (cl != null) try {
			impl = cl.loadClass("modules." + name.replace('/', '.') + ".Intrinsics");
			System.out.println("loaded intrinsics for module " + name);
		} catch (ClassNotFoundException e) {
		} catch(IllegalArgumentException | SecurityException | ClassCastException e) {
			e.printStackTrace();
		}
		this.moduleImpl = impl;
		loadTraces();
		this.loaded = IntrinsicLoader.preInit(this, impl);
	}

	public void loadTraces() {
		if (Main.TRACES == null || trace0 >= 0) return;
		trace0 = 2;
		Path path = this.path.resolve("traces.tga");
		if (Files.exists(path)) try (
			InputStream is = Files.newInputStream(path);
			MemoryStack ms = MemoryStack.stackPush();
		) {
			trace0 = Main.TRACES.load(GLUtils.readTGA(is, ms), this);
			System.out.println("loaded traces for module " + name);
		} catch (IOException e) {
			System.err.printf("can't load traces for module %s\n because %s\n", name, e);
		}
	}

	public Module ensureLoaded() {
		//checking loaded twice may seem redundant but
		//it avoids unnecessarily entering the synchronized block
		if (!loaded) load();
		return this;
	}

	private synchronized void load() {
		if (loaded) return;
		Intrinsics.loadModule(this);
		IntrinsicLoader.linkAll(this, moduleImpl);
		System.out.println("loaded content of module " + name);
		loaded = true;
	}

	public Type findType(String name) {
		ensureLoaded();
		Type vt = types.get(name);
		if (vt != null) return vt;
		for (Module m : imports.values())
			if ((vt = m.ensureLoaded().types.get(name)) != null)
				return vt;
		return null;
	}

	public BlockDef getBlock(String name) {
		ensureLoaded();
		return blocks.get(name);
	}

	public String name(Module mod) {
		//TODO add missing
		return modNames.get(mod);
	}

	public Path icon(String name) {
		return path.resolve("icons/" + name + ".tga");
	}

	@Override
	public String toString() {
		return name;
	}

}
