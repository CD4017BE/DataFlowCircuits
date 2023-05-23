package modules.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.function.Function;

import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.Module.PaletteGroup;
import cd4017be.dfc.lang.builders.ConstList;
import cd4017be.dfc.lang.instructions.ConstantIns;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Impl;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Init;

/**
 * @author cd4017be */
public class Intrinsics {

	public static Type VOID, STRING, INT, BLOCKTYPE, ARGTYPE, TYPE, MODULE, BLOCK, MODEL, PINS, PALETTE;
	public static Value NULL;

	public static final ArgumentParser ERROR_ARG =
	(arg, block, argidx, context, idx) -> {
		throw new SignalError(idx, "Invalid Argument");
	};
	public static final Function<BlockDef, NodeAssembler> ERROR = 
	def -> (block, context, idx) -> {
		throw new SignalError(idx, "Invalid Block");
	};
	public static final NodeAssembler OUTPUT = (block, context, idx) -> {
		String[] args = context.args(block);
		if (block.outs() != 0 || args.length != block.ins())
			throw new SignalError(idx, "wrong IO count");
		for (int i = 0; i < args.length; i++) {
			Node node = block.parser(i).parse(args[i], block, i, context, idx);
			if (node.in.length == 0)
				throw new SignalError(idx, "argument node has no inputs");
			block.ins[i] = node.in[0];
		}
	};
	public static final NodeAssembler INPUT = (block, context, idx) -> {
		String[] args = context.args(block);
		if (block.ins() != 0 || args.length != block.outs())
			throw new SignalError(idx, "wrong IO count");
		for (int i = 0; i < args.length; i++)
			block.outs[i] = block.parser(i).parse(args[i], block, i, context, idx);
	};
	private static final Function<BlockDef, NodeAssembler> IMPORT =
	def -> new NodeAssembler() {
		@Override
		public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
			INPUT.assemble(block, context, idx);
		}
		@Override
		public BlockDef openCircuit(BlockDesc block, NodeContext context) {
			if (block.args.length == 0) return null;
			Module m = LoadingCache.getModule(block.args[0]);
			return m.getBlock("");
		}
	};
	private static final Function<BlockDef, NodeAssembler> BLOCKDEF =
	def -> new cd4017be.dfc.lang.builders.Function(def) {
		@Override
		public BlockDef openCircuit(BlockDesc block, NodeContext context) {
			if (context == null || block.args.length == 0) return null;
			return context.def.module.getBlock(block.args[0]);
		}
	};

	@Init(phase = Init.OVERRIDE)
	public static void init(Module m) {
		//NodeAssemblers
		Function<BlockDef, NodeAssembler> in, out, func;
		m.assemblers.put("error", ERROR);
		m.assemblers.put("in", in = def -> INPUT);
		m.assemblers.put("out", out = def -> OUTPUT);
		m.assemblers.put("func", func = cd4017be.dfc.lang.builders.Function::new);
		m.assemblers.put("const", ConstList::new);
		//ArgumentParsers
		ArgumentParser io, str, num, bt, at, def, module, model;
		m.parsers.put("error", ERROR_ARG);
		m.parsers.put("io", io = new ArgumentParser() {
			@Override
			public Node parse(String arg, BlockDesc block, int argidx, NodeContext context, int idx) throws SignalError {
				return context.getIO(arg);
			}
			@Override
			public void getAutoCompletions(BlockDesc block, int arg, ArrayList<String> list, NodeContext context) {
				list.addAll(context.links.keySet());
			}
		});
		m.parsers.put("str", str = (arg, block, argidx, context, idx) -> 
			ConstantIns.node(Value.of(arg, STRING), idx)
		);
		m.parsers.put("num", num = (arg, block, argidx, context, idx) -> {
			int n = -1; try {
				n = Integer.parseInt(arg);
			} catch (NumberFormatException e) {}
			if (n < 0) throw new SignalError(idx, "invalid number");
			return ConstantIns.node(Value.of(n, STRING), idx);
		});
		m.parsers.put("bt", bt = new ArgumentParser() {
			@Override
			public Node parse(
				String arg, BlockDesc block, int argidx, NodeContext context, int idx
			) throws SignalError {
				return ConstantIns.node(Value.of(arg, BLOCKTYPE), idx);
			}
			@Override
			public void getAutoCompletions(
				BlockDesc block, int arg, ArrayList<String> list, NodeContext context
			) {
				Module m = module(context, block, 0);
				if (m != null) list.addAll(m.assemblers.keySet());
			}
		});
		m.parsers.put("at", at = new ArgumentParser() {
			@Override
			public Node parse(
				String arg, BlockDesc block, int argidx, NodeContext context, int idx
			) throws SignalError {
				return ConstantIns.node(Value.of(arg, ARGTYPE), idx);
			}
			@Override
			public void getAutoCompletions(
				BlockDesc block, int arg, ArrayList<String> list, NodeContext context
			) {
				Module m = module(context, block, 0);
				if (m != null) list.addAll(m.parsers.keySet());
			}
		});
		m.parsers.put("def", def = new ArgumentParser() {
			@Override
			public Node parse(
				String arg, BlockDesc block, int argidx, NodeContext context, int idx
			) throws SignalError {
				return ConstantIns.node(Value.of(arg, STRING), idx);
			}
			@Override
			public void getAutoCompletions(
				BlockDesc block, int arg, ArrayList<String> list, NodeContext context
			) {
				Module m = module(context, block, 0);
				if (m == null) return;
				list.addAll(m.types.keySet());
				list.addAll(m.blocks.keySet());
				list.addAll(m.palettes.keySet());
			}
		});
		m.parsers.put("module", module = new ArgumentParser() {
			@Override
			public Node parse(
				String arg, BlockDesc block, int argidx, NodeContext context, int idx
			) throws SignalError {
				return ConstantIns.node(Value.of(arg, MODULE), idx);
			}
			@Override
			public void getAutoCompletions(
				BlockDesc block, int arg, ArrayList<String> list, NodeContext context
			) {
				LoadingCache.listAllModules(list);
			}
		});
		m.parsers.put("model", model = new ArgumentParser() {
			@Override
			public Node parse(
				String arg, BlockDesc block, int argidx, NodeContext context, int idx
			) throws SignalError {
				return ConstantIns.node(Value.of(arg, MODEL), idx);
			}
			@Override
			public void getAutoCompletions(
				BlockDesc block, int arg, ArrayList<String> list, NodeContext context
			) {
				Module m = module(context, block, 0);
				if (m == null) return;
				Path root = m.path.resolve("icons");
				try {
					Files.walk(root).forEach(path -> {
						String name = root.relativize(path).toString();
						if (name.endsWith(".tga"))
							list.add(name.substring(0, name.length() - 4));
					});
				} catch(IOException e) {}
			}
		});
		//Blocks
		new BlockDef(m, "missing", ERROR,
			BlockDef.EMPTY_IO,
			BlockDef.EMPTY_IO,
			BlockDef.EMPTY_IO,
			new ArgumentParser[0],
			null, "Missing Block"
		);
		new BlockDef(m, "in", in,
			BlockDef.EMPTY_IO,
			new String[] {"signal#"},
			new String[] {"name#"},
			new ArgumentParser[] {io},
			m.icon("in"),
			"use named signal"
		);
		new BlockDef(m, "out", out,
			new String[] {"signal#"},
			BlockDef.EMPTY_IO,
			new String[] {"name#"},
			new ArgumentParser[] {io},
			m.icon("out"),
			"define named signal"
		);
		new BlockDef(m, "newstr", in,
			BlockDef.EMPTY_IO,
			new String[] {"string#"},
			new String[] {"text#"},
			new ArgumentParser[] {str},
			m.icon("str"),
			"string constant"
		);
		new BlockDef(m, "import", IMPORT,
			BlockDef.EMPTY_IO,
			new String[] {"module#"},
			new String[] {"path#"},
			new ArgumentParser[] {module},
			m.icon("import"),
			"import module"
		);
		new BlockDef(m, "getmodel", func,
			new String[] {"module"},
			new String[] {"model"},
			new String[] {"path"},
			new ArgumentParser[] {model},
			m.icon("model"),
			"block icon"
		);
		new BlockDef(m, "getblocktype", func,
			new String[] {"module"},
			new String[] {"blocktype"},
			new String[] {"name"},
			new ArgumentParser[] {bt},
			m.icon("blocktype"),
			"block type"
		);
		new BlockDef(m, "getargtype", func,
			new String[] {"module"},
			new String[] {"argtype"},
			new String[] {"name"},
			new ArgumentParser[] {at},
			m.icon("argtype"),
			"argument type"
		);
		new BlockDef(m, "getdef", func,
			new String[] {"module"},
			new String[] {"object"},
			new String[] {"name"},
			new ArgumentParser[] {def},
			m.icon("getdef"),
			"get module element"
		);
		new BlockDef(m, "newpins", func,
			new String[] {"argtype#"},
			new String[] {"iolist"},
			new String[] {"name#"},
			new ArgumentParser[] {str},
			m.icon("pins"),
			"I/O name list"
		);
		new BlockDef(m, "defblock", BLOCKDEF,
			new String[] {"blocktype", "inlist", "outlist", "arglist", "model", "displayname"},
			BlockDef.EMPTY_IO,
			new String[] {"name"},
			new ArgumentParser[] {str},
			m.icon("block"),
			"define block"
		);
		new BlockDef(m, "deftype", func,
			BlockDef.EMPTY_IO,
			BlockDef.EMPTY_IO,
			new String[] {"name", "offcolor", "oncolor"},
			new ArgumentParser[] {str, num, num},
			m.icon("type"),
			"define signal type"
		);
		new BlockDef(m, "defpalette", func,
			new String[] {"block#"},
			BlockDef.EMPTY_IO,
			new String[] {"name"},
			new ArgumentParser[] {str},
			m.icon("palette"),
			"define block palette"
		);
		//Palettes
		new PaletteGroup(m, "module creation", new String[] {
			"loader\0in", "loader\0out", "loader\0newstr", "loader\0import",
			"loader\0getmodel", "loader\0getblocktype", "loader\0getargtype", "loader\0getdef",
			"loader\0newpins", "loader\0defblock", "loader\0deftype", "loader\0defpalette"
		});
		//Types
		VOID = new Type(m, "void", 1, 0);
		STRING = new Type(m, "string", 2, 2);
		INT = new Type(m, "int", 3, 3);
		MODULE = new Type(m, "module", 4, 4);
		MODEL = new Type(m, "model", 5, 5);
		BLOCK = new Type(m, "block", 6, 6);
		TYPE = new Type(m, "type", 7, 7);
		PINS = new Type(m, "pins", 8, 8);
		BLOCKTYPE = new Type(m, "blocktype", 9, 9);
		ARGTYPE = new Type(m, "argtype", 10, 10);
		PALETTE = new Type(m, "palette", 11, 11);
		//Values
		NULL = new Value(VOID, Value.NO_ELEM, Value.NO_DATA, 0);
	}

	private static Module module(NodeContext context, BlockDesc block, int in) {
		Value v = block.signal(block.outs() + in);
		if (v == null) return null;
		return v.type == MODULE ? LoadingCache.getModule(v.dataAsString()) : context.def.module;
	}

	private static Module module(Value v) {
		return v.type == MODULE ? LoadingCache.getModule(v.dataAsString()) : null;
	}

	private static Function<BlockDef, NodeAssembler> blocktype(Value v, Module m) {
		if (v.type != BLOCKTYPE) return null;
		Module m1 = v.elements.length > 0 ? module(v.elements[0]) : null;
		return (m1 != null ? m1 : m).assemblers.get(v.dataAsString());
	}

	private static ArgumentParser argtype(Value v, Module m) {
		if (v.type != ARGTYPE) return null;
		Module m1 = v.elements.length > 0 ? module(v.elements[0]) : null;
		return (m1 != null ? m1 : m).parsers.get(v.dataAsString());
	}

	private static String[] pins(Value v) {
		int l = (int)v.value;
		if (v.type != PINS || l > v.elements.length) return BlockDef.EMPTY_IO;
		String[] names = new String[l];
		for (int i = 0; i < l; i++)
			names[i] = v.elements[i].dataAsString();
		return names;
	}

	private static ArgumentParser[] parsers(Value v, Module m) {
		int l = v.type == PINS ? (int)v.value : 0;
		ArgumentParser[] parsers = new ArgumentParser[l];
		if (l * 2 > v.elements.length)
			Arrays.fill(parsers, ERROR_ARG);
		else for (int i = 0; i < l; i++)
			if ((parsers[i] = argtype(v.elements[l + i], m)) == null)
				parsers[i] = ERROR_ARG;
		return parsers;
	}

	private static Path model(Value v, Module m) {
		if (v.type != MODEL) return null;
		Module m1 = v.elements.length > 0 ? module(v.elements[0]) : null;
		return (m1 != null ? m1 : m).icon(v.dataAsString());
	}

	/**Load the contents for the given module from its data structure file.
	 * @param m module to load */
	public static void loadModule(Module m) {
		BlockDef mdef = m.blocks.get("");
		m.imports.clear();
		m.blocks.clear();
		m.types.clear();
		m.palettes.clear();
		if (mdef == null) mdef = new BlockDef(m);
		else m.blocks.put(mdef.id, mdef);
		if (!(mdef.assembler instanceof ConstList cl)) return;
		var content = cl.signals();
		for (Entry<String, Value> e : content.entrySet()) {
			String k = e.getKey();
			Value v = e.getValue();
			if (v.type == MODULE) {
				m.imports.put(k, LoadingCache.getModule(v.dataAsString()));
			} else if (v.type == BLOCK) {
				if (!k.equals(v.dataAsString())) continue;
				if (v.elements.length < 6) continue;
				var type = blocktype(v.elements[0], m);
				if (type == null) type = ERROR;
				var parsers = parsers(v.elements[3], m);
				var ins = pins(v.elements[1]);
				var outs = pins(v.elements[2]);
				var args = pins(v.elements[3]);
				var model = model(v.elements[4], m);
				var name = v.elements[5].dataAsString();
				new BlockDef(m, k, type, ins, outs, args, parsers, model, name);
			} else if (v.type == TYPE) {
				if (!k.equals(v.dataAsString())) continue;
				new Type(m, k, (int)v.value, (int)(v.value >> 32));
			} else if (v.type == PALETTE) {
				String[] pal;
				if (v.elements.length > 0 && v.elements[0].type == MODULE) {
					Module module = LoadingCache.getModule(v.elements[0].dataAsString());
					PaletteGroup pg = module.ensureLoaded().palettes.get(v.dataAsString());
					if (pg == null) continue;
					pal = pg.blocks;
				} else if (k.equals(v.dataAsString())) {
					pal = new String[v.elements.length];
					for (int i = 0; i < pal.length; i++) {
						Value el = v.elements[i];
						pal[i] = (el.elements.length > 0 && el.elements[0].type == MODULE
							? el.elements[0].dataAsString() : m.name)
							+ '\0' + el.dataAsString();
					}
				} else continue;
				new PaletteGroup(m, k, pal);
			}
		}
	}

	private static Value packWithModule(Value v, Value module) {
		if (module.type == MODULE)
			return new Value(v.type, new Value[] {module}, v.data, 0);
		if (module.equals(NULL))
			return v;
		throw new IllegalArgumentException("invalid module input");
	}

	@Impl(inputs = 2)
	public static Value getmodel(Value name, Value module) {
		return packWithModule(name, module);
	}

	@Impl(useIp = true, inputs = 2)
	public static Value getblocktype(Interpreter ip, Value name, Value module) {
		name = packWithModule(name, module);
		if (blocktype(name, ip.task().root.module) == null)
			throw new IllegalArgumentException("block type doesn't exist");
		return name;
	}

	@Impl(useIp = true, inputs = 2)
	public static Value getargtype(Interpreter ip, Value name, Value module) {
		name = packWithModule(name, module);
		if (argtype(name, ip.task().root.module) == null)
			throw new IllegalArgumentException("argument type doesn't exists");
		return name;
	}

	@Impl(inputs = 2)
	public static Value newpins(Value[] names, Value[] types) {
		int l = types.length;
		if (l == 0) return Value.of(PINS);
		Value[] pins;
		if (types[0].equals(NULL)) {
			pins = names;
			for (Value t : types)
				if (!t.equals(NULL))
					throw new IllegalArgumentException("invalid argument types");
		} else {
			pins = new Value[l * 2];
			for (int i = 0; i < l; i++) {
				pins[i] = names[i];
				Value t = types[i];
				pins[i + l] = t;
				if (t.type != ARGTYPE)
					throw new IllegalArgumentException("invalid argument types");
			}
		}
		return new Value(PINS, pins, Value.NO_DATA, l);
	}

	private static Value checkType(Value v, Type t, String msg) {
		if (v.equals(NULL)) return Value.of(t);
		if (v.type == t) return v;
		throw new IllegalArgumentException(msg);
	}

	@Impl(inputs = 7)
	public static Value defblock(byte[] name, Value type, Value ins, Value outs, Value args, Value model, Value dspname) {
		if (type.type != BLOCKTYPE) throw new IllegalArgumentException("invalid block type");
		ins = checkType(ins, PINS, "invalid input list");
		outs = checkType(outs, PINS, "invalid output list");
		args = checkType(args, PINS, "invalid argument list");
		if (args.elements.length != args.value * 2)
			throw new IllegalArgumentException("argument list is missing types");
		model = checkType(model, MODEL, "invalid model");
		dspname = checkType(dspname, STRING, "invalid display name");
		return new Value(BLOCK, new Value[] {type, ins, outs, args, model, dspname}, name, 0);
	}

	@Impl(inputs = 3)
	public static Value deftype(byte[] name, long offcolor, long oncolor) {
		return new Value(TYPE, Value.NO_ELEM, name, offcolor & 0xffffffffL | oncolor << 32);
	}

	@Impl(inputs = 2)
	public static Value defpalette(byte[] name, Value[] blocks) {
		for (int i = 0; i < blocks.length; i++)
			if (blocks[i].type != BLOCK)
				throw new IllegalArgumentException("element " + i + " is not a block");
		return new Value(PALETTE, blocks, name, 0);
	}

	@Impl(inputs = 2)
	public static Value getdef(Value name, Value module) {
		if (module.type != MODULE)
			throw new IllegalArgumentException("invalid module");
		String s = name.dataAsString();
		Module m = LoadingCache.getModule(module.dataAsString()).ensureLoaded();
		Type type;
		if (m.blocks.containsKey(s)) type = BLOCK;
		else if (m.types.containsKey(s)) type = TYPE;
		else if (m.palettes.containsKey(s)) type = PALETTE;
		else throw new IllegalArgumentException("element not found");
		return new Value(type, new Value[] {module}, name.data, 0);
	}

}
