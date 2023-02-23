package cd4017be.compiler;

import static cd4017be.compiler.LoadingCache.CORE;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.lwjgl.system.MemoryStack;

import cd4017be.compiler.builtin.Bundle;
import cd4017be.compiler.builtin.IOStream;
import cd4017be.compiler.instr.ConstList;
import cd4017be.util.*;
import cd4017be.util.ConfigFile.KeyValue;

/**
 * 
 * @author CD4017BE */
public class Module {

	public final Path path;
	public final Plugin plugin;
	public final LinkedHashMap<String, Module> imports = new LinkedHashMap<>();
	public final HashMap<Module, String> modNames = new HashMap<>();
	public final HashMap<String, BlockDef> blocks = new LinkedHashMap<>();
	public final HashMap<String, BlockModel> models = new HashMap<>();
	public final HashMap<String, VTable> types = new HashMap<>();
	public final ArrayList<SignalProvider> signals = new ArrayList<>();
	public final ArrayList<PaletteGroup> groups = new ArrayList<>();
	private boolean loaded;
	int trace0 = -1;

	public Module(Path path) {
		this.path = path;
		Plugin plugin = Plugin.DEFAULT;
		if (Files.isRegularFile(path.resolve("Plugin.class")))
			try {
				plugin = (Plugin)new PluginClassLoader(path).loadClass("Plugin")
				.getDeclaredConstructor().newInstance();
				System.out.println("loaded plugin for module " + path);
			} catch(
				ClassNotFoundException | InstantiationException
				| IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException
				| SecurityException | ClassCastException e
			) { e.printStackTrace(); }
		this.plugin = plugin;
		loadTraces();
		try {
			loadModelDescriptions();
		} catch(IOException e) {
			models.clear();
			e.printStackTrace();
		}
	}

	public void loadTraces() {
		if (LoadingCache.TRACES == null || trace0 >= 0) return;
		Path path = this.path.resolve("traces.tga");
		trace0 = 1;
		if (Files.isReadable(path)) try (
			InputStream is = Files.newInputStream(path);
			MemoryStack ms = MemoryStack.stackPush();
		) {
			trace0 = LoadingCache.TRACES.load(GLUtils.readTGA(is, ms), this);
			System.out.println("loaded traces for module " + this.path);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadModelDescriptions() throws IOException {
		Object[] data =  ConfigFile.parse(Files.newBufferedReader(path.resolve("models.cfg")));
		try {
			for (Object e0 : data) {
				KeyValue kv0 = (KeyValue)e0;
				String key = kv0.key();
				BlockModel model = new BlockModel(this, key);
				for (Object e1 : (Object[])kv0.value()) {
					KeyValue kv1 = (KeyValue)e1;
					switch(kv1.key()) {
					case "out" -> model.outs = parseIO((Object[])kv1.value());
					case "in" -> model.ins = parseIO((Object[])kv1.value());
					case "rep" -> {
						Object[] arr = (Object[])kv1.value();
						model.setRepRegion(
							((Number)arr[0]).intValue(),
							((Number)arr[1]).intValue(),
							((Number)arr[2]).intValue(),
							((Number)arr[3]).intValue()
						);
					}
					case "text" -> {
						Object[] arr = (Object[])kv1.value();
						model.setTextRegion(
							((Number)arr[0]).intValue(),
							((Number)arr[1]).intValue(),
							((Number)arr[2]).intValue(),
							((Number)arr[3]).intValue()
						);
					}
					}
				}
				models.put(key, model);
			}
		} catch (ClassCastException | ArrayIndexOutOfBoundsException e) {
			throw new IOException(e);
		}
		System.out.println("loaded models for module " + path);
	}

	private static byte[] parseIO(Object[] arr) {
		byte[] val = new byte[arr.length * 2];
		int i = 0;
		for (Object e : arr) {
			Object[] arr1 = (Object[])e;
			val[i++] = ((Number)arr1[0]).byteValue();
			val[i++] = ((Number)arr1[1]).byteValue();
		}
		return val;
	}

	public Module ensureLoaded() {
		if (!loaded) try {
			loaded = true;
			load();
		} catch (IOException e) {
			imports.clear();
			modNames.clear();
			blocks.clear();
			e.printStackTrace();
		}
		return this;
	}

	private void load() throws IOException {
		Object[] data = ConfigFile.parse(Files.newBufferedReader(path.resolve("module.cfg")));
		try {
			if (this != CORE) {
				imports.put("core", CORE);
				modNames.put(CORE, "core");
			}
			for (Object e0 : data) {
				KeyValue kv0 = (KeyValue)e0;
				switch(kv0.key()) {
				case "modules" -> {
					for (Object e1 : (Object[])kv0.value()) {
						KeyValue kv1 = (KeyValue)e1;
						Path p = path.resolveSibling((String)kv1.value()).normalize();
						Module m = LoadingCache.getModule(p);
						imports.put(kv1.key(), m);
						modNames.put(m, kv1.key());
					}}
				case "blocks" -> {
					PaletteGroup pg;
					if (kv0.value() instanceof KeyValue kv) {
						pg = new PaletteGroup(kv.key());
						kv0 = kv;
					} else pg = new PaletteGroup("");
					groups.add(pg);
					for (Object e1 : (Object[])kv0.value()) {
						KeyValue kv1 = (KeyValue)e1;
						String type = null;
						Object[] out = {}, in = out, arg = out;
						String name = kv1.key();
						BlockModel model = LoadingCache.MISSING_MODEL;
						int scale = 0;
						for (Object e2 : (Object[])kv1.value()) {
							KeyValue kv2 = (KeyValue)e2;
							switch(kv2.key()) {
							case "type" -> type = (String)kv2.value();
							case "model" -> {
								String s = (String)kv2.value();
								int i = s.indexOf(':');
								Module m = this;
								if (i >= 0) {
									m = imports.get(s.substring(0, i));
									if (m == null)
										throw new IOException("module for block model not defined: " + s);
									s = s.substring(i + 1);
								}
								model = m.models.getOrDefault(s, LoadingCache.MISSING_MODEL);
							}
							case "name" -> name = (String)kv2.value();
							case "in" -> in = (Object[])kv2.value();
							case "out" -> out = (Object[])kv2.value();
							case "arg" -> arg = (Object[])kv2.value();
							case "var" -> {
								for (Object o3 : (Object[])kv2.value())
									switch((String)o3) {
									case "out" -> scale |= BlockDef.VAR_OUT;
									case "in" -> scale |= BlockDef.VAR_IN;
									case "arg" -> scale |= BlockDef.VAR_ARG;
									}
								}
							}
						}
						BlockDef def = new BlockDef(
							this, kv1.key(), type,
							Arrays.copyOf(in, in.length, String[].class),
							Arrays.copyOf(out, out.length, String[].class),
							Arrays.copyOf(arg, arg.length, String[].class),
							model, scale
						);
						def.name = name;
						blocks.put(def.id, def);
						pg.blocks().add(def);
					}}
				case "types" -> {
					for (Object e1 : (Object[])kv0.value()) {
						KeyValue kv1 = (KeyValue)e1;
						Object[] ops = new Object[0];
						String text = "";
						int color = 0;
						Class<? extends Value> vc = Value.class;
						for (Object e2 : (Object[])kv1.value()) {
							KeyValue kv2 = (KeyValue)e2;
							switch(kv2.key()) {
							case "text" -> text = (String)kv2.value();
							case "color" -> color = ((Number)kv2.value()).intValue();
							case "class" -> vc = plugin.valueClass((String)kv2.value());
							case "ops" -> ops = (Object[])kv2.value();
							}
						}
						VTable vt = new VTable(this, kv1.key(), text, color, vc);
						types.put(kv1.key(), vt);
						for (Object e2 : ops) {
							KeyValue kv2 = (KeyValue)e2;
							BlockDef def = blocks.get(kv2.value());
							if (def != null && def.assembler instanceof Instruction vm)
								vt.put(kv2.key(), vm);
						}
					}}
				case "signals" -> {
					for (Object e1 : (Object[])kv0.value()) {
						String name = (String)e1;
						int i = name.indexOf(':');
						Module m = this;
						if (i >= 0) {
							m = imports.get(name.substring(0, i));
							name = name.substring(i + 1);
						}
						if (m != null)
							signals.add(new SignalProvider(m, name));
					}}
				}
			}
			for (VTable vt : types.values())
				vt.initInternal();
		} catch (ClassCastException e) {
			throw new IOException(e);
		}
		System.out.println("loaded content of module " + path);
	}

	public void save() throws IOException {
		try(ConfigWriter cw = new ConfigWriter(Files.newBufferedWriter(path.resolve("module.cfg")), " ")) {
			cw.optKeyArray("modules", sortKeys(imports), this::writeImport, true);
			cw.optKeyArray("blocks", sortKeys(blocks), this::writeBlock, true);
		}
	}

	private void writeImport(ConfigWriter cw, String key) throws IOException {
		cw.key(key).val(imports.get(key).path.relativize(path.getParent()).toString());
	}

	private void writeBlock(ConfigWriter cw, String key) throws IOException {
		BlockDef def = blocks.get(key);
		cw.key(key).begin().nl();
		cw.key("name").val(def.name).nl();
		cw.key("type").val(def.type).nl();
		cw.key("model").val(modelName(def.model)).nl();
		cw.optKeyArray("out", def.outs, ConfigWriter::val, false);
		cw.optKeyArray("in", def.outs, ConfigWriter::val, false);
		cw.optKeyArray("arg", def.outs, ConfigWriter::val, false);
		cw.end();
	}

	private String modelName(BlockModel model) {
		if (model.module == this) return model.name;
		String name = modNames.get(model.module);
		return name == null ? "" : name + ":" + model.name;
	}

	private static String[] sortKeys(HashMap<String, ?> map) {
		String[] arr = map.keySet().toArray(String[]::new);
		Arrays.sort(arr);
		return arr;
	}

	public VTable findType(String name) {
		ensureLoaded();
		VTable vt = types.get(name);
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

	public NodeAssembler assembler(String type, BlockDef blockDef) {
		NodeAssembler ass = plugin.assembler(type, blockDef);
		if (ass != null) return ass;
		for (Module m : imports.values())
			if ((ass = m.plugin.assembler(type, blockDef)) != null)
				return ass;
		return null;
	}

	public Value signal(String name) {
		for (SignalProvider sp : signals) {
			ConstList cl = sp.signals();
			Value v;
			if (cl != null && (v = cl.getValue(name)) != null)
				return v;
		}
		switch(name) {
		case "stdout": return new IOStream(IOStream.IO, System.out);
		default: return Bundle.VOID;
		}
	}

	@Override
	public String toString() {
		return path.toString();
	}

	static class SignalProvider {
		final Module module;
		final String name;
		ConstList signals;

		SignalProvider(Module module, String name) {
			this.module = module;
			this.name = name;
		}

		ConstList signals() {
			if (signals != null) return signals;
			BlockDef def = module.getBlock(name);
			if (def != null && def.assembler instanceof ConstList cl)
				return signals = cl;
			return null;
		}
	}

	public static record PaletteGroup(String name, ArrayList<BlockDef> blocks) {
		public PaletteGroup(String name) {this(name, new ArrayList<>());}
	}

}
