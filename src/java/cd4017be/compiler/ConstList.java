package cd4017be.compiler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import cd4017be.compiler.builtin.Bundle;
import cd4017be.util.Profiler;

/**
 * @author CD4017BE */
public class ConstList implements NodeAssembler.TextAutoComplete {

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
	public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
		if (ins != 0) return NodeAssembler.err(macro, def, outs, ins, "wrong IO count");
		ensureLoaded();
		String name = args.length > 0 ? args[0] : def.outs[0];
		Value val = signals.get(name);
		if (val == null) val = Bundle.VOID;
		return macro.addNode(NodeOperator.CONST, val, ins).makeLinks(outs);
	}

	@Override
	public void getAutoCompletions(Macro macro, BlockDef def, int arg, ArrayList<String> list) {
		ensureLoaded();
		list.addAll(signals.keySet());
	}

	public SignalError compile() {
		Profiler p = new Profiler(System.out);
		Context cont = new Context();
		cont.setInputs();
		Macro macro = new Macro(def);
		cont.run(macro);
		p.end("graph setup");
		if (!cont.tick(Integer.MAX_VALUE))
			return new SignalError("took too long");
		p.end("executed");
		if (cont.error != null) return cont.error;
		String[] keys = (String[])macro.out().data;
		Value value = cont.outVal;
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
		return null;
	}

}
