package cd4017be.compiler.instr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.*;
import cd4017be.util.Profiler;

/**
 * @author CD4017BE */
public class ConstList implements NodeAssembler {

	final BlockDef def;
	HashMap<String, Value> signals;

	public ConstList(BlockDef def) {
		this.def = def;
	}

	private void ensureLoaded() {
		if (signals != null) return;
		signals = new HashMap<>();
		try {
			CircuitFile.readSignals(def, signals);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
		if (block.ins.length != 0)
			throw new SignalError(idx, "wrong IO count");
		ensureLoaded();
		String name = block.args.length > 0 ? block.args[0] : def.outs[0];
		Value val = signals.get(name);
		if (val == null) val = Bundle.VOID;
		Node node = new Constant(val).node(idx);
		block.setOuts(node);
	}

	@Override
	public void
	getAutoCompletions(BlockDesc desc, int arg, ArrayList<String> list, NodeContext context) {
		ensureLoaded();
		list.addAll(signals.keySet());
	}

	public void compile() throws IOException, SignalError {
		Profiler p = new Profiler(System.out);
		Function f = new Function(def);
		String[] keys = f.compile();
		p.end("compiled");
		Value value = f.eval(Arguments.EMPTY, ScopeData.ROOT);
		p.end("executed");
		this.signals = new HashMap<>();
		if (keys.length == 1)
			signals.put(keys[0], value);
		else if (value instanceof Bundle b)
			for (int i = 0; i < keys.length; i++)
			signals.put(keys[i], b.values[i]);
		try {
			CircuitFile.writeSignals(def, signals);
		} catch (IOException e) {
			e.printStackTrace();
		}
		p.end("written to file");
	}

}
