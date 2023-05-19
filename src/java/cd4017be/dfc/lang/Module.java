package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.LoadingCache.CORE;
import static cd4017be.dfc.modules.core.Intrinsics.NULL;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.lang.builders.BasicConstructs;
import cd4017be.dfc.lang.builders.ConstList;
import cd4017be.dfc.lang.builders.Function;
import cd4017be.dfc.lang.builders.Macro;
import cd4017be.dfc.lang.builders.SwitchBuilder;
import cd4017be.dfc.lang.instructions.IntrinsicLoader;
import cd4017be.util.*;
import cd4017be.util.ConfigFile.KeyValue;

/**
 * 
 * @author CD4017BE */
public class Module {

	public final String name;
	protected final URL source;
	public final LinkedHashMap<String, Module> imports = new LinkedHashMap<>();
	public final HashMap<Module, String> modNames = new HashMap<>();
	public final HashMap<String, BlockDef> blocks = new LinkedHashMap<>();
	public final HashMap<String, Type> types = new HashMap<>();
	public final ArrayList<SignalProvider> signals = new ArrayList<>();
	public final ArrayList<PaletteGroup> groups = new ArrayList<>();
	public final HashMap<String, java.util.function.Function<BlockDef, NodeAssembler>> assemblers = new HashMap<>();
	public final HashMap<String, ArgumentParser> parsers = new HashMap<>();
	private final Class<?> moduleImpl;
	private boolean loaded;
	int trace0 = -1;

	public Module(String path, Class<?> intr) {
		this.name = path;
		this.source = resolve(path);
		this.moduleImpl = intr != null ? intr : loadIntrinsicsClass(source);
		loadTraces();
	}

	private static URL resolve(String path) {
		URL url = LoadingCache.class.getResource("/cd4017be/dfc/modules/" + path + "/module.cfg");
		if (url != null) return url;
		Path p = Path.of("src/dfc", path, "module.cfg");
		if (Files.exists(p)) try {
			return p.toUri().toURL();
		} catch(MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Class<?> loadIntrinsicsClass(URL source) {
		if (source == null) return null;
		try {
			Class<?> c = new PluginClassLoader(source).loadClass("Intrinsics");
			System.out.println("loaded intrinsics for module " + source);
			return c;
		} catch (ClassNotFoundException e) {
		} catch(IllegalArgumentException | SecurityException | ClassCastException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void loadTraces() {
		if (Main.TRACES == null || trace0 >= 0) return;
		trace0 = 2;
		if (source == null) return;
		try (
			InputStream is = new URL(source, "traces.tga").openStream();
			MemoryStack ms = MemoryStack.stackPush();
		) {
			trace0 = Main.TRACES.load(GLUtils.readTGA(is, ms), this);
			System.out.println("loaded traces for module " + name);
		} catch (FileNotFoundException e) {}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Module ensureLoaded() {
		if (loaded) return this;
		synchronized(this) {
			if (!loaded) try {
				loaded = true;
				load();
			} catch (IOException e) {
				imports.clear();
				modNames.clear();
				blocks.clear();
				e.printStackTrace();
			}
		}
		return this;
	}

	private void load() throws IOException {
		if (source == null) return;
		Object[] data = ConfigFile.parse(new InputStreamReader(source.openStream()));
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
						Module m = LoadingCache.getModule((String)kv1.value());
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
						String model = "";
						int scale = 0;
						for (Object e2 : (Object[])kv1.value()) {
							KeyValue kv2 = (KeyValue)e2;
							switch(kv2.key()) {
							case "type" -> type = (String)kv2.value();
							case "model" -> model = (String)kv2.value();
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
						pg.blocks().add(def);
					}}
				case "types" -> {
					for (Object e1 : (Object[])kv0.value()) {
						KeyValue kv1 = (KeyValue)e1;
						Object[] ops = new Object[0];
						int color0 = 0, color1 = 0;
						for (Object e2 : (Object[])kv1.value()) {
							KeyValue kv2 = (KeyValue)e2;
							switch(kv2.key()) {
							case "color" -> {
								Object[] arr = (Object[])kv2.value();
								color0 = ((Number)arr[0]).intValue();
								color1 = ((Number)arr[1]).intValue();
							}
							case "ops" -> ops = (Object[])kv2.value();
							}
						}
						Type vt = new Type(this, kv1.key(), color0, color1);
						types.put(kv1.key(), vt);
						for (Object e2 : ops) {
							KeyValue kv2 = (KeyValue)e2;
							BlockDef def = blocks.get(kv2.value());
							if (def != null) vt.put(kv2.key(), def);
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
		} catch (ClassCastException e) {
			throw new IOException(e);
		}
		if (moduleImpl != null) IntrinsicLoader.linkAll(this, moduleImpl);
		System.out.println("loaded content of module " + name);
	}

//	public void save() throws IOException {
//		try(ConfigWriter cw = new ConfigWriter(Files.newBufferedWriter(path.resolve("module.cfg")), " ")) {
//			cw.optKeyArray("modules", sortKeys(imports), this::writeImport, true);
//			cw.optKeyArray("blocks", sortKeys(blocks), this::writeBlock, true);
//		}
//	}

//	private void writeImport(ConfigWriter cw, String key) throws IOException {
//		cw.key(key).val(imports.get(key).path.relativize(path.getParent()).toString());
//	}

//	private void writeBlock(ConfigWriter cw, String key) throws IOException {
//		BlockDef def = blocks.get(key);
//		cw.key(key).begin().nl();
//		cw.key("name").val(def.name).nl();
//		cw.key("type").val(def.type).nl();
//		cw.key("model").val(def.modelId).nl();
//		cw.optKeyArray("out", def.outs, ConfigWriter::val, false);
//		cw.optKeyArray("in", def.outs, ConfigWriter::val, false);
//		cw.optKeyArray("arg", def.outs, ConfigWriter::val, false);
//		cw.end();
//	}

//	private static String[] sortKeys(HashMap<String, ?> map) {
//		String[] arr = map.keySet().toArray(String[]::new);
//		Arrays.sort(arr);
//		return arr;
//	}

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

	public ArgumentParser parser(String type) {
		return switch(type) {
		case "str" -> BasicConstructs.STRING_ARG;
		case "val" -> BasicConstructs.VALUE_ARG;
		case "io" -> BasicConstructs.IO_ARG;
		default -> BasicConstructs.ERROR_ARG;
		};
	}

	public NodeAssembler assembler(String type, BlockDef def) {
		return switch(type) {
		case "macro" -> new Macro(def);
		case "func" -> new Function(def);
		case "const" -> new ConstList(def);
		case "swt" -> new SwitchBuilder(def);
		case "in" -> BasicConstructs.INPUT;
		case "out" -> BasicConstructs.OUTPUT;
		case "dep" -> BasicConstructs.DEPEND;
		case "pack" -> BasicConstructs.PACK;
		case "loop" -> BasicConstructs.LOOP;
		case "vc" -> BasicConstructs.VIRTUAL;
		default -> BasicConstructs.ERROR;
		};
	}

	public Value signal(String name) {
		for (SignalProvider sp : signals) {
			ConstList cl = sp.signals();
			Value v;
			if (cl != null && (v = cl.getValue(name)) != null)
				return v;
		}
		return NULL;
	}

	@Override
	public String toString() {
		return name;
	}

	public static class SignalProvider {
		final Module module;
		final String name;
		ConstList signals;

		SignalProvider(Module module, String name) {
			this.module = module;
			this.name = name;
		}

		public ConstList signals() {
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
