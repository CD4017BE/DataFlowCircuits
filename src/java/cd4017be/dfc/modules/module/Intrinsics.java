package cd4017be.dfc.modules.module;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import cd4017be.dfc.lang.ArgumentParser;
import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.BlockDesc;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.NodeContext;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Type;
import cd4017be.dfc.lang.Value;
import cd4017be.dfc.lang.builders.BasicConstructs;
import cd4017be.dfc.lang.instructions.ConstantIns;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Init;

/**
 * @author cd4017be */
public class Intrinsics {

	public static Type STRING, BLOCKTYPE, ARGTYPE, TYPE, MODULE, BLOCK, MODEL, PINS;

	private static Value newString(Type type, String s, int n) {
		byte[] data = s == null || s.isEmpty() ? Value.NO_DATA : s.getBytes(UTF_8);
		return new Value(type, Value.NO_ELEM, data, n);
	}

	public static boolean preInit(Module m) {
		//NodeAssemblers
		m.assemblers.put("in", def -> BasicConstructs.INPUT);
		m.assemblers.put("out", def -> BasicConstructs.OUTPUT);
		m.assemblers.put("func", cd4017be.dfc.lang.builders.Function::new);
		//ArgumentParsers
		m.parsers.put("io", BasicConstructs.IO_ARG);
		m.parsers.put("str", (arg, block, argidx, context, idx) -> 
			ConstantIns.node(newString(STRING, arg, 0), idx)
		);
		m.parsers.put("int", (arg, block, argidx, context, idx) -> {
			int n = -1; try {
				n = Integer.parseInt(arg);
			} catch (NumberFormatException e) {}
			if (n < 0) throw new SignalError(idx, "invalid number");
			return ConstantIns.node(new Value(STRING, Value.NO_ELEM, Value.NO_DATA, n), idx);
		});
		m.parsers.put("bt", new ArgumentParser() {
			@Override
			public Node parse(
				String arg, BlockDesc block, int argidx,
				NodeContext context, int idx
			) throws SignalError {
				if (!context.def.module.assemblers.containsKey(arg))
					throw new SignalError(idx, "invalid block type");
				return ConstantIns.node(newString(BLOCKTYPE, arg, 0), idx);
			}
			@Override
			public void getAutoCompletions(
				BlockDesc block, int arg, ArrayList<String> list,
				NodeContext context
			) {
				list.addAll(context.def.module.assemblers.keySet());
			}
		});
		m.parsers.put("at", new ArgumentParser() {
			@Override
			public Node parse(
				String arg, BlockDesc block, int argidx,
				NodeContext context, int idx
			) throws SignalError {
				if (!context.def.module.parsers.containsKey(arg))
					throw new SignalError(idx, "invalid argument type");
				return ConstantIns.node(newString(ARGTYPE, arg, 0), idx);
			}
			@Override
			public void getAutoCompletions(
				BlockDesc block, int arg, ArrayList<String> list,
				NodeContext context
			) {
				list.addAll(context.def.module.parsers.keySet());
			}
		});
		m.parsers.put("module", null);
		m.parsers.put("model", null);
		//Blocks
		new BlockDef(m, "in", "in", "in",
			BlockDef.EMPTY_IO,
			new String[] {"object#"},
			new String[] {"name#"},
			new String[] {"io"}
		);
		new BlockDef(m, "out", "out", "out",
			new String[] {"object#"},
			BlockDef.EMPTY_IO,
			new String[] {"name#"},
			new String[] {"io"}
		);
		new BlockDef(m, "newstring", "in", "str",
			BlockDef.EMPTY_IO,
			new String[] {"string#"},
			new String[] {"text#"},
			new String[] {"str"}
		);
		new BlockDef(m, "import", "in", "import",
			BlockDef.EMPTY_IO,
			new String[] {"module#"},
			new String[] {"path#"},
			new String[] {"module"}
		);
		new BlockDef(m, "getmodel", "func", "model",
			new String[] {"module"},
			new String[] {"model#"},
			new String[] {"path#"},
			new String[] {"model"}
		);
		new BlockDef(m, "getblocktype", "func", "blocktype",
			new String[] {"module"},
			new String[] {"blocktype#"},
			new String[] {"name#"},
			new String[] {"bt"}
		);
		new BlockDef(m, "getargtype", "func", "argtype",
			new String[] {"module"},
			new String[] {"argtype#"},
			new String[] {"name#"},
			new String[] {"at"}
		);
		new BlockDef(m, "newpins", "func", "pins",
			new String[] {"argtype#"},
			new String[] {"iolist"},
			new String[] {"name#"},
			new String[] {"str"}
		);
		new BlockDef(m, "defblock", "block", "block",
			new String[] {"blocktype", "model", "displayname", "inlist", "outlist", "arglist"},
			BlockDef.EMPTY_IO,
			new String[] {"name"},
			new String[] {"io"}
		);
		new BlockDef(m, "deftype", "type", "type",
			BlockDef.EMPTY_IO,
			BlockDef.EMPTY_IO,
			new String[] {"name", "offcolor", "oncolor"},
			new String[] {"io", "int", "int"}
		);
		//Types
		new Type(m, "string", 0, 0);
		new Type(m, "module", 1, 1);
		new Type(m, "model", 2, 2);
		new Type(m, "blocktype", 3, 3);
		new Type(m, "argtype", 4, 4);
		new Type(m, "pins", 5, 5);
		new Type(m, "block", 6, 6);
		new Type(m, "type", 7, 7);
		return false;
	}

	@Init
	public static void init(Module m) {
	}

}
