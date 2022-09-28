package cd4017be.dfc.lang;

import static java.lang.invoke.MethodHandleProxies.asInterfaceInstance;

import java.io.IOException;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

import cd4017be.dfc.compiler.CompileError;
import cd4017be.dfc.compiler.NodeCompiler;
import cd4017be.dfc.graph.*;

/**
 * 
 * @author CD4017BE */
public class BlockRegistry {

	private final HashMap<String, BlockDef> defs = new HashMap<>();
	private final ArrayDeque<BlockDef> toLoad = new ArrayDeque<>();
	public final Path[] sourcePaths;
	private final ArrayList<Plugin> plugins = new ArrayList<>();

	public BlockRegistry(String... sourcePaths) {
		this.sourcePaths = new Path[sourcePaths.length];
		FileSystem fs = FileSystems.getDefault();
		for (int i = 0; i < sourcePaths.length; i++)
			this.sourcePaths[i] = fs.getPath(sourcePaths[i]);
	}

	public BlockDef get(String name) {
		BlockDef def = defs.get(name);
		if (def != null) return def;
		try (CircuitFile file = openFile(name, false)) {
			def = file.readInterface(name);
			defs.put(name, def);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return def;
	}

	public BlockDef get(String name, int out, int in, boolean arg) {
		BlockDef def = get(name);
		if (def == null) return new BlockDef(name, out, in, arg);
		BlockDef def1 = def.withIO(out, in, arg);
		//if (def1.behavior == null) toLoad.add(def1);
		return def1;
	}

	public CircuitFile openFile(String name, boolean write) throws IOException {
		name += ".dfc";
		Path path = null;
		for (Path p : sourcePaths)
			if (Files.exists(path = p.resolve(name))) break;
		return new CircuitFile(path, write);
	}

	public Behavior load(BlockDef def) {
		String name = def.name;
		BlockDef loaded = defs.get(name);
		if (loaded == null) loaded = def;
		if (loaded.behavior == null) {
			try (CircuitFile file = openFile(name, false)) {
				BlockInfo[] blocks = file.readCircuit(this);
				if (blocks == null) defineIntrinsic(loaded);
				else {
					loaded.behavior = new Circuit(loaded, blocks);
					loaded.compiler = (ni, c) -> {throw new CompileError(ni.node, "unresolved macro");};
				}
			} catch(IOException e) {
				e.printStackTrace();
				loaded.behavior = Behavior.NULL;
			}
		}
		return loaded.copyTo(def).behavior;
	}

	public void load() {
		for (BlockDef def; (def = toLoad.poll()) != null;) load(def);
	}

	public void addPlugin(Lookup lookup, Class<?> typeCheck, Class<?> compile) {
		plugins.add(new Plugin(lookup, typeCheck, compile));
	}

	private void defineIntrinsic(BlockDef def) {
		String name = def.name;
		if (javaKeywords.contains(name)) name = '_' + name;
		else name = name.replace('#', '_');
		for (Plugin p : plugins) {
			try {
				def.behavior = asInterfaceInstance(
					Behavior.class, p.lookup.findStatic(p.typeCheck, name, BEHAVIOR)
				);
			} catch(NoSuchMethodException | IllegalAccessException e) {
				continue;
			}
			try {
				def.compiler = asInterfaceInstance(
					NodeCompiler.class, p.lookup.findStatic(p.compile, name, COMPILER)
				);
			} catch(NoSuchMethodException | IllegalAccessException e) {
				def.compiler = NodeCompiler.NULL;
			}
			return;
		}
		def.behavior = Behavior.NULL;
		def.compiler = NodeCompiler.NULL;
		System.err.printf("missing intrinsic definition for %s\n", def.name);
	}


	private static final Set<String> javaKeywords = Set.of(
		"void", "boolean", "byte", "short", "char", "int", "long", "float", "double",
		"private", "public", "protected", "default", "static", "final", "const", "new",
		"throws", "class", "interface", "enum", "abstract", "import", "package", "volatile",
		"strictfp", "if", "do", "while", "for", "goto", "break", "continue", "try", "catch",
		"return", "switch", "case", "else"
	);
	private static final MethodType BEHAVIOR, COMPILER;
	static {
		defbehavior: {
			for (Method m : Behavior.class.getDeclaredMethods())
				if (!m.isDefault()) {
					BEHAVIOR = MethodType.methodType(m.getReturnType(), m.getParameterTypes());
					break defbehavior;
				}
			throw new IllegalStateException();
		}
		defcompiler: {
			for (Method m : NodeCompiler.class.getDeclaredMethods())
				if (!m.isDefault()) {
					COMPILER = MethodType.methodType(m.getReturnType(), m.getParameterTypes());
					break defcompiler;
				}
			throw new IllegalStateException();
		}
	}

	public static record Plugin(Lookup lookup, Class<?> typeCheck, Class<?> compile) {}

}
