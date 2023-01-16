package cd4017be.compiler;

import java.util.HashMap;
import cd4017be.compiler.builtin.ScopeData;
import cd4017be.compiler.instr.ConstList;
import cd4017be.compiler.instr.Function;
import cd4017be.util.IndexedSet;
import cd4017be.util.Profiler;

/**
 * 
 * @author CD4017BE */
public class NodeContext {

	public final BlockDef def;
	public final HashMap<String, Node> links = new HashMap<>();
	public final Arguments env;
	public Arguments state;

	public NodeContext(BlockDef def, boolean env) {
		this.def = def;
		this.env = env ? new Arguments(new Value[def.ins.length]) : null;
	}

	public Node getIO(String name) {
		return links.computeIfAbsent(name, n -> new Node(Instruction.PASS, Node.INSTR, 1, -1));
	}

	public String[] args(BlockDesc block) {
		return block.args;
	}

	public void build(IndexedSet<? extends BlockDesc> blocks, boolean addIns) throws SignalError {
		links.clear();
		if (addIns)
			for (int i = 0; i < def.ins.length; i++)
				links.put(def.ins[i], new Node(i));
		for (int i = 0; i < blocks.size(); i++) {
			BlockDesc block = blocks.get(i);
			block.def.assembler.assemble(block, this, i);
		}
		for (BlockDesc block : blocks)
			block.connect();
	}

	public String[] collectOutputs(Node out) {
		String[] names = def.assembler instanceof ConstList
			? links.keySet().toArray(String[]::new) : def.outs;
		if (names.length != 1) {
			Node res = new Node(Instruction.PACK, Node.INSTR, names.length, Integer.MAX_VALUE);
			out.in[0].connect(res);
			for (int i = 0; i < names.length; i++)
				res.in[i].connect(links.get(names[i]));
		} else out.in[0].connect(links.get(names[0]));
		return names;
	}

	public Arguments typeCheck(IndexedSet<? extends BlockDesc> blocks) throws SignalError {
		state = null;
		Profiler p = new Profiler(System.out);
		build(blocks, true);
		Node out = new Node();
		collectOutputs(out);
		p.end("assembled");
		Function f = def.assembler instanceof Function ff ? ff : new Function(def);
		f.define(out);
		p.end("compiled");
		state = new Arguments(f, env.resetLimit(), ScopeData.ROOT);
		try {
			for (CodeBlock block = f.first; block != null;)
				block = state.run(block);
		} catch (SignalError e) {
			throw e.resolvePos(f.vars);
		}
		p.end("executed");
		return state;
	}

}
