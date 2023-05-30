package modules.dfc.module;

import java.net.URL;
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

	private static final boolean BOOTSTRAP = false;

	public static Type VOID, STRING, INT, BLOCKTYPE, ARGTYPE, TYPE, MODULE, BLOCK, MODEL, PINS, PALETTE;
	public static Value NULL;

	public static final ArgumentParser ERROR_ARG =
	(arg, block, argidx, context, idx) -> {
		throw new SignalError(idx, "Invalid Argument");
	};
	public static final NodeAssembler ERROR_ASM =
	(block, context, idx) -> {
		throw new SignalError(idx, "Invalid Block");
	};
	public static final Function<BlockDef, NodeAssembler> ERROR = def -> ERROR_ASM;
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
	private static final NodeAssembler IMPORT = new NodeAssembler() {
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

	@Init
	public static void init(Module m) {
		//NodeAssemblers
		Function<BlockDef, NodeAssembler> in, out, func, imp;
		m.assemblers.put("error", ERROR);
		m.assemblers.put("in", in = def -> INPUT);
		m.assemblers.put("out", out = def -> OUTPUT);
		m.assemblers.put("func", func = cd4017be.dfc.lang.builders.Function::new);
		m.assemblers.put("const", ConstList::new);
		m.assemblers.put("import", imp = def -> IMPORT);
		m.assemblers.put("blockdef", BLOCKDEF);
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
				if (m != null) list.addAll(m.data().keySet());
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
				if (m != null) m.listIcons(list);
			}
		});
		//Types
		VOID = m.getType("void");
		STRING = m.getType("string");
		INT = m.getType("int");
		MODULE = m.getType("module");
		MODEL = m.getType("model");
		BLOCK = m.getType("block");
		TYPE = m.getType("type");
		PINS = m.getType("pins");
		BLOCKTYPE = m.getType("blocktype");
		ARGTYPE = m.getType("argtype");
		PALETTE = m.getType("palette");
		//Values
		NULL = new Value(VOID, Value.NO_ELEM, Value.NO_DATA, 0);
		
		/* The following model content is usually defined through a circuit.
		 * But in order to create that circuit in the first place,
		 * the circuit editor needs these to be already defined.
		 * The solution to this chicken-egg-problem is hard-coding. */
		if (!BOOTSTRAP) return;
		VOID.define(1, 0);
		STRING.define(2, 2);
		INT.define(3, 3);
		MODULE.define(4, 4);
		MODEL.define(5, 5);
		BLOCK.define(6, 6);
		TYPE.define(7, 7);
		PINS.define(8, 8);
		BLOCKTYPE.define(9, 9);
		ARGTYPE.define(10, 10);
		PALETTE.define(11, 11);
		//Blocks
		BlockDef[] pal = {
			m.getBlock("in").define(in,
				BlockDef.EMPTY_IO,
				new String[] {"signal#"},
				new String[] {"name#"},
				new ArgumentParser[] {io},
				m.icon("in"),
				"use named signal"
			),
			m.getBlock("out").define(out,
				new String[] {"signal#"},
				BlockDef.EMPTY_IO,
				new String[] {"name#"},
				new ArgumentParser[] {io},
				m.icon("out"),
				"define named signal"
			),
			m.getBlock("newstr").define(in,
				BlockDef.EMPTY_IO,
				new String[] {"string#"},
				new String[] {"text#"},
				new ArgumentParser[] {str},
				m.icon("str"),
				"string constant"
			),
			m.getBlock("import").define(imp,
				BlockDef.EMPTY_IO,
				new String[] {"module#"},
				new String[] {"path#"},
				new ArgumentParser[] {module},
				m.icon("import"),
				"import module"
			),
			m.getBlock("getmodel").define(func,
				new String[] {"module"},
				new String[] {"model"},
				new String[] {"path"},
				new ArgumentParser[] {model},
				m.icon("model"),
				"block icon"
			),
			m.getBlock("getblocktype").define(func,
				new String[] {"module"},
				new String[] {"blocktype"},
				new String[] {"name"},
				new ArgumentParser[] {bt},
				m.icon("blocktype"),
				"block type"
			),
			m.getBlock("getargtype").define(func,
				new String[] {"module"},
				new String[] {"argtype"},
				new String[] {"name"},
				new ArgumentParser[] {at},
				m.icon("argtype"),
				"argument type"
			),
			m.getBlock("getdef").define(func,
				new String[] {"module"},
				new String[] {"object"},
				new String[] {"name"},
				new ArgumentParser[] {def},
				m.icon("getdef"),
				"get module element"
			),
			m.getBlock("newpins").define(func,
				new String[] {"argtype#"},
				new String[] {"iolist"},
				new String[] {"name#"},
				new ArgumentParser[] {str},
				m.icon("pins"),
				"I/O name list"
			),
			m.getBlock("defblock").define(BLOCKDEF,
				new String[] {"blocktype", "inlist", "outlist", "arglist", "model", "displayname"},
				BlockDef.EMPTY_IO,
				new String[] {"name"},
				new ArgumentParser[] {str},
				m.icon("block"),
				"define block"
			),
			m.getBlock("deftype").define(func,
				BlockDef.EMPTY_IO,
				BlockDef.EMPTY_IO,
				new String[] {"name", "offcolor", "oncolor"},
				new ArgumentParser[] {str, num, num},
				m.icon("type"),
				"define signal type"
			),
			m.getBlock("defpalette").define(func,
				new String[] {"block#"},
				BlockDef.EMPTY_IO,
				new String[] {"name"},
				new ArgumentParser[] {str},
				m.icon("palette"),
				"define block palette"
			)
		};
		//Palettes
		new PaletteGroup(m, "module creation", pal);
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

	private static URL model(Value v, Module m) {
		if (v.type != MODEL) return null;
		Module m1 = v.elements.length > 0 ? module(v.elements[0]) : null;
		return (m1 != null ? m1 : m).icon(v.dataAsString());
	}

	/**Load the contents for the given module from its data structure file.
	 * @param m module to load */
	public static void loadModule(Module m) {
		var content = m.data();
		for (Entry<String, Value> e : content.entrySet()) {
			String k = e.getKey();
			Value v = e.getValue();
			if (v.type == MODULE) {
				m.imports.put(k, LoadingCache.getModule(v.dataAsString()));
			} else if (v.type == PALETTE) {
				BlockDef[] pal;
				if (v.elements.length > 0 && v.elements[0].type == MODULE) {
					Module module = LoadingCache.getModule(v.elements[0].dataAsString());
					PaletteGroup pg = module.loadPalettes().palettes.get(v.dataAsString());
					if (pg == null) continue;
					pal = pg.blocks;
				} else if (k.equals(v.dataAsString())) {
					pal = new BlockDef[v.elements.length];
					for (int i = 0; i < pal.length; i++) {
						Value el = v.elements[i];
						Module mod = el.elements.length > 0 && el.elements[0].type == MODULE
							? LoadingCache.getModule(el.elements[0].dataAsString()) : m;
						pal[i] = mod.getBlock(el.dataAsString());
					}
				} else continue;
				new PaletteGroup(m, k, pal);
			}
		}
	}

	public static boolean loadBlock(BlockDef def) {
		Value v = def.module.data().get(def.id);
		if (v == null || v.type != BLOCK || v.elements.length < 6 || !def.id.equals(v.dataAsString()))
			return false;
		var type = blocktype(v.elements[0], def.module);
		if (type == null) type = ERROR;
		var parsers = parsers(v.elements[3], def.module);
		var ins = pins(v.elements[1]);
		var outs = pins(v.elements[2]);
		var args = pins(v.elements[3]);
		var model = model(v.elements[4], def.module);
		var name = v.elements[5].dataAsString();
		def.define(type, ins, outs, args, parsers, model, name);
		return true;
	}

	public static boolean loadType(Type type) {
		Value v = type.module.data().get(type.id);
		if (v == null || v.type != TYPE || !type.id.equals(v.dataAsString()))
			return false;
		type.define((int)(v.value >> 32), (int)v.value);
		return true;
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
	public static Value deftype(byte[] name, long oncolor, long offcolor) {
		return new Value(TYPE, Value.NO_ELEM, name, oncolor & 0xffffffffL | offcolor << 32);
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
		Value v = LoadingCache.getModule(module.dataAsString()).data().get(name.dataAsString());
		if (v == null) throw new IllegalArgumentException("element not found");
		if (v.type == MODULE || v.elements.length == 1 && v.elements[0].type == MODULE)
			return v;
		return new Value(v.type, new Value[] {module}, v.data, 0);
	}

}
