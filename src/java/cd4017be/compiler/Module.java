package cd4017be.compiler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

import cd4017be.util.ConfigFile;
import cd4017be.util.ConfigFile.KeyValue;
import cd4017be.util.ConfigWriter;

/**
 * 
 * @author CD4017BE */
public class Module {

	public final Path path;
	public final LoadingCache cache;
	public final Plugin plugin;
	public final HashMap<String, Module> imports = new HashMap<>();
	public final HashMap<Module, String> modNames = new HashMap<>();
	public final HashMap<String, BlockDef> blocks = new HashMap<>();
	public final HashMap<String, BlockModel> models = new HashMap<>();
	public final HashMap<String, Value> signals = new HashMap<>();
	private boolean loaded;

	public Module(LoadingCache cache, Path path) {
		this.cache = cache;
		this.path = path;
		Plugin plugin = Plugin.DEFAULT;
		if (Files.isRegularFile(path.resolve("Plugin.class")))
			try {
				plugin = (Plugin)new PluginClassLoader(path).loadClass("Plugin")
				.getDeclaredConstructor().newInstance();
			} catch(
				ClassNotFoundException | InstantiationException
				| IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException
				| SecurityException | ClassCastException e
			) { e.printStackTrace(); }
		this.plugin = plugin;
		if (cache.icons != null) try {
			loadModelDescriptions();
		} catch(IOException e) {
			models.clear();
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
					case "out" -> model.outs = parseIO((Object[])kv0.value());
					case "in" -> model.ins = parseIO((Object[])kv0.value());
					case "size" -> {
						Object[] arr = (Object[])kv0.value();
						model.tw = ((Number)arr[0]).byteValue();
						model.th = ((Number)arr[1]).byteValue();
					}
					case "text" -> {
						Object[] arr = (Object[])kv0.value();
						model.tx = ((Number)arr[0]).byteValue();
						model.ty = ((Number)arr[1]).byteValue();
					}
					}
				}
				models.put(key, model);
			}
		} catch (ClassCastException e) {
			throw new IOException(e);
		}
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
			for (Object e0 : data) {
				KeyValue kv0 = (KeyValue)e0;
				switch(kv0.key()) {
				case "modules" -> {
					for (Object e1 : (Object[])kv0.value()) {
						KeyValue kv1 = (KeyValue)e1;
						Path p = path.resolveSibling((String)kv1.value()).normalize();
						Module m = cache.getModule(p);
						imports.put(kv1.key(), m);
						modNames.put(m, kv1.key());
					}}
				case "blocks" -> {
					for (Object e1 : (Object[])kv0.value()) {
						KeyValue kv1 = (KeyValue)e1;
						String type = null;
						Object[] out = {}, in = out, arg = out;
						String name = kv1.key();
						BlockModel model = cache.defaultModel;
						for (Object e2 : (Object[])kv1.value()) {
							KeyValue kv2 = (KeyValue)e2;
							switch(kv2.key()) {
							case "type" -> type = (String)kv2.value();
							case "model" -> {
								if (cache.icons == null) break;
								String s = (String)kv2.value();
								int i = s.indexOf(':');
								Module m = this;
								if (i >= 0) {
									m = imports.get(s.substring(0, i));
									if (m == null)
										throw new IOException("module for block model not defined: " + s);
									s = s.substring(i + 1);
								}
								model = m.models.get(s);
							}
							case "name" -> name = (String)kv2.value();
							case "in" -> in = (Object[])kv2.value();
							case "out" -> out = (Object[])kv2.value();
							case "arg" -> arg = (Object[])kv2.value();
							}
						}
						BlockDef def = new BlockDef(
							this, kv1.key(), type,
							Arrays.copyOf(in, in.length, String[].class),
							Arrays.copyOf(out, out.length, String[].class),
							Arrays.copyOf(arg, arg.length, String[].class),
							model
						);
						def.name = name;
						blocks.put(def.id, def);
					}}
				}
			}
		} catch (ClassCastException e) {
			throw new IOException(e);
		}
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

	public BlockDef findIO(String name) {
		BlockDef def = blocks.get(name);
		if (def != null && def.assembler == NodeAssembler.IO) return def;
		for (Module m : imports.values())
			if ((def = m.blocks.get(name)) != null && def.assembler == NodeAssembler.IO)
				return def;
		return null;
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

}
