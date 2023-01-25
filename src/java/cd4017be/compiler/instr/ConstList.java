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
		String[] args = context.args(block);
		if (args.length == 0) args = def.outs;
		if (block.ins() != 0 || block.outs() != args.length)
			throw new SignalError(idx, "wrong IO count");
		ensureLoaded();
		for (int i = 0; i < args.length; i++) {
			Value val = signals.get(args[i]);
			if (val == null) throw new SignalError(idx, "invalid name " + args[i]);
			block.outs[i] = new Node(val, Node.INSTR, 0, idx);
		}
	}

	@Override
	public void
	getAutoCompletions(BlockDesc desc, int arg, ArrayList<String> list, NodeContext context) {
		ensureLoaded();
		list.addAll(signals.keySet());
	}

	public Value getValue(String name) {
		ensureLoaded();
		return signals.get(name);
	}

	public void compile() throws IOException, SignalError {
		Profiler p = new Profiler(System.out);
		Function f = new Function(def);
		String[] keys = f.compile();
		p.end("built");
		ScopeData root = new ScopeData(Bundle.VOID);
		Value value = f.eval(Arguments.EMPTY.resetLimit(), root);
		p.end("evaluated");
		root.compile(def);
		p.end("compiled");
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
