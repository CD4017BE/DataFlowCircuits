package cd4017be.dfc.lang.builders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.instructions.ConstantIns;
import cd4017be.dfc.modules.core.Intrinsics;
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
		synchronized(this) {
			if (signals == null) try {
				CircuitFile.readSignals(def, signals = new HashMap<>());
			} catch (IOException e) {
				e.printStackTrace();
			}
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
			block.outs[i] = ConstantIns.node(val, idx);
		}
	}

	@Override
	public void
	getAutoCompletions(BlockDesc desc, int arg, ArrayList<String> list, NodeContext context) {
		ensureLoaded();
		list.addAll(signals.keySet());
	}

	@Override
	public Instruction makeVirtual(BlockDef def) {
		if (def.args.length != 0 || def.ins.length != 0 || def.outs.length != 1) return null;
		ensureLoaded();
		Value val = signals.get(def.outs[0]);
		return val == null ? null : new ConstantIns(val);
	}

	public Value getValue(String name) {
		ensureLoaded();
		return signals.get(name);
	}

	public void compile(Interpreter ip) throws SignalError {
		Profiler p = new Profiler(System.out);
		Function f = new Function(def);
		String[] keys;
		synchronized(def) {
			keys = f.compile();
		}
		p.end("built");
		Value[] state = new Value[f.vars.length];
		state[0] = Intrinsics.NULL;
		ip.new Task(f.code, state, 1000000, t -> {
			t.log();
			if (t.error != null) {
				t.error.printStackTrace();
				return;
			}
			Value value = t.vars[f.ret];
			Value[] elem = keys.length == 1 ? new Value[]{value} : value.elements;
			synchronized(this) {
				try {
					CircuitFile.writeSignals(def, keys, elem);
				} catch (IOException e) {
					e.printStackTrace();
				}
				signals = new HashMap<>();
				for (int i = 0; i < keys.length; i++)
					signals.put(keys[i], elem[i]);
			}
		});
	}

}
