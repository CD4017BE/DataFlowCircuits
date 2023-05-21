package cd4017be.dfc.lang.builders;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.instructions.ConstantIns;
import cd4017be.util.Profiler;

/**
 * @author CD4017BE */
public class ConstList implements NodeAssembler, ArgumentParser {

	final BlockDef def;
	private HashMap<String, Value> signals;

	public ConstList(BlockDef def) {
		this.def = def;
	}

	public HashMap<String, Value> signals() {
		if (signals != null) return signals;
		synchronized(this) {
			if (signals == null) try {
				CircuitFile.readSignals(def, signals = new HashMap<>());
			} catch (IOException e) {
				System.err.printf("can't load data structure of %s\n because %s\n", def, e);
			}
			return signals;
		}
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
		String[] args = context.args(block);
		if (args.length == 0) args = def.outs;
		if (block.ins() != 0 || block.outs() != args.length)
			throw new SignalError(idx, "wrong IO count");
		signals();
		for (int i = 0; i < args.length; i++)
			block.outs[i] = parse(args[i], block, i, context, idx);
	}

	@Override
	public void
	getAutoCompletions(BlockDesc desc, int arg, ArrayList<String> list, NodeContext context) {
		list.addAll(signals().keySet());
	}

	@Override
	public Instruction makeVirtual(BlockDef def) {
		if (def.args.length != 0 || def.ins.length != 0 || def.outs.length != 1) return null;
		Value val = signals().get(def.outs[0]);
		return val == null ? null : new ConstantIns(val);
	}

	public Value getValue(String name) {
		return signals().get(name);
	}

	public void compile(Interpreter ip, Value scope) throws SignalError {
		Profiler p = new Profiler(System.out);
		Function f = new Function(def);
		String[] keys;
		synchronized(def) {
			keys = f.compile();
		}
		p.end("built");
		Value[] state = new Value[f.vars.length];
		state[0] = scope;
		ip.new Task(def, f.code, state, 1000000, t -> {
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

	@Override
	public Node parse(
		String arg, BlockDesc block, int argidx, NodeContext context, int idx
	) throws SignalError {
		Value val = signals.get(arg);
		if (val == null) throw new SignalError(idx, "invalid name " + arg);
		return ConstantIns.node(val, idx);
	}

}
