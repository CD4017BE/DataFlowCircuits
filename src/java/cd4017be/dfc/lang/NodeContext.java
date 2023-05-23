package cd4017be.dfc.lang;

import java.util.HashMap;
import java.util.function.Consumer;
import cd4017be.dfc.lang.Interpreter.Task;
import cd4017be.dfc.lang.Node.SignalConflict;
import cd4017be.dfc.lang.builders.ConstList;
import cd4017be.dfc.lang.builders.Function;
import cd4017be.dfc.lang.instructions.PackIns;
import cd4017be.util.IndexedSet;
import cd4017be.util.Profiler;
import modules.core.Intrinsics;

/**
 * 
 * @author CD4017BE */
public class NodeContext {

	public final BlockDef def;
	public final HashMap<String, Node> links = new HashMap<>();
	public final Value[] env;

	public NodeContext(BlockDef def, boolean env) {
		this.def = def;
		this.env = env ? Intrinsics.elemNew((def.assembler instanceof Function f ? f.par : def.ins.length) + 1) : null;
	}

	public Node getIO(String name) {
		return links.computeIfAbsent(name, n -> new Node(null, Node.PASS, 1, -1));
	}

	public String[] args(BlockDesc block) {
		return block.args;
	}

	public void build(IndexedSet<? extends BlockDesc> blocks, boolean addIns) throws SignalError {
		links.clear();
		for (int i = 0; i < blocks.size(); i++) {
			BlockDesc block = blocks.get(i);
			try {
				block.def.assembler.assemble(block, this, i);
			} catch (SignalConflict e) {
				throw new SignalError(i, "signal conflict");
			}
		}
		for (int i = 0; i < blocks.size(); i++)
			try {
				blocks.get(i).connect();
			} catch (SignalConflict e) {
				throw new SignalError(i, "signal conflict");
			}
		if (addIns) {
			int i = 0;
			for (String arg : def.args) {
				Node node = links.get(arg);
				if (node != null && node.in[0].from == null)
					node.in[0].connect(new Node(i));
				i++;
			}
			for (String in : def.ins) {
				Node node = links.get(in);
				if (node != null && node.in[0].from == null)
					node.in[0].connect(new Node(i));
				i++;
			}
		}
	}

	public String[] collectOutputs(Node out) {
		String[] names = def.assembler instanceof ConstList
			? links.keySet().toArray(String[]::new) : def.outs;
		if (names.length != 1) {
			Node res = new Node(new PackIns(), Node.INSTR, names.length, Integer.MAX_VALUE);
			out.in[0].connect(res);
			for (int i = 0; i < names.length; i++)
				res.in[i].connect(links.get(names[i]));
		} else out.in[0].connect(links.get(names[0]));
		return names;
	}

	public void typeCheck(Interpreter ip, IndexedSet<? extends BlockDesc> blocks, Consumer<Task> after) {
		try {
			Profiler p = new Profiler(System.out);
			build(blocks, true);
			Node out = new Node();
			collectOutputs(out);
			p.end("assembled");
			Function f = def.assembler instanceof Function ff ? ff : new Function(def);
			f.define(out);
			p.end("compiled");
			Value[] state = new Value[f.vars.length];
			for (int i = 0; i <= f.par; i++)
				state[i] = env[i];
			ip.new Task(def, f.code, state, 1000000, after);
		} catch (SignalError e) {
			ip.new Task(def, e, after);
		}
	}

}
