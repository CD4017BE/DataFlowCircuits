package cd4017be.compiler;

import static cd4017be.compiler.NodeOperator.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import cd4017be.util.ExtInputStream;

/**
 * @author CD4017BE */
public class Macro implements NodeAssembler, VirtualMethod {

	int nodeCount;
	public final BlockDef def;
	HashMap<String, Node> links;
	Node[] nodes;
	protected boolean loaded;

	public Macro(BlockDef def) {
		this.def = def;
		this.nodes = new Node[Math.max(1, Integer.highestOneBit(def.ins.length + def.outs.length - 1 << 1))];
		this.links = new HashMap<>();
	}

	public void clear() {
		loaded = false;
		Arrays.fill(nodes, 0, nodeCount, null);
		nodeCount = 0;
		links.clear();
	}

	public Node addNode(NodeOperator op, Object data, int ins) {
		int i = nodeCount++;
		if (i >= nodes.length) nodes = Arrays.copyOf(nodes, i * 2);
		return nodes[i] = new Node(op, data, ins, i);
	}

	public Node addIONode(String name) {
		return links.computeIfAbsent(name, n -> addNode(PASS, n, 1));
	}

	public int ins() {
		return def.ins.length;
	}

	public Node in(int i) {
		return nodes[i + 1];
	}

	public Node out() {
		return nodes[0];
	}

	@Override
	public int[] assemble(Macro macro, BlockDef def, int outs, int ins, String[] args) {
		if (ins != ins()) return NodeAssembler.err(macro, def, outs, ins, "wrong IO count");
		int[] io = new int[ins + outs];
		Node in = macro.addNode(MACRO, this, ins);
		if (outs == 1) io[0] = in.idx;
		else {
			in.outs = new int[outs];
			for (int i = 0; i < outs; i++) {
				Node n = macro.addNode(ELEMENT, i, 1);
				in.outs[i] = io[i] = n.idx;
				n.ins[0] = in.idx;
			}
		}
		for (int i = 0; i < ins; i++)
			io[i + outs] = in.idx(i);
		return io;
	}

	public Macro ensureLoaded(Context context) {
		if (!loaded) load();
		return this;
	}

	public void load() {
		//load data
		BlockInfo[] blocks;
		try (ExtInputStream is = CircuitFile.readBlock(def)) {
			blocks = CircuitFile.readCircuit(is, def.module);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		clear();
		//create output and input nodes
		String[] outs = def.outs;
		Node out = addNode(OUTPUT, null, outs.length);
		for (int i = 0; i < ins(); i++)
			links.put(def.ins[i], addNode(INPUT, i, 0));
		//assemble block nodes
		int[][] arr = new int[blocks.length][];
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo info = blocks[i];
			arr[i] = info.def.assembler.assemble(this, info.def, info.outs, info.ins.length, info.args);
		}
		//connect macro outputs
		if (def.assembler instanceof ConstList) {
			outs = links.keySet().toArray(String[]::new);
			int i = out.idx;
			out = nodes[i] = new Node(OUTPUT, outs, outs.length, i);
		}
		for (int i = 0; i < outs.length; i++) {
			Node n = links.get(outs[i]);
			out.ins[i] = n == null ? -1 : n.idx;
		}
		//connect blocks
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo info = blocks[i];
			int o = info.outs;
			int[] idxs = arr[i];
			int[] ins = info.ins;
			for (int j = 0; j < ins.length; j++) {
				int k = ins[j], l = k < 0 ? -1 : arr[k & 0xffff][k >> 16];
				int m = idxs[o + j];
				nodes[m & 0xffffff].ins[m >>> 24] = l;
			}
		}
		//count used outputs
		for (int i = 0; i < nodeCount; i++) {
			Node n = nodes[i];
			for (int j : n.ins)
				if (j >= 0)
					nodes[j].usedOuts++;
		}
		//allocate output index arrays
		for (int i = 0; i < nodeCount; i++) {
			Node n = nodes[i];
			n.outs = new int[n.usedOuts];
			n.usedOuts = 0;
		}
		//assign output indices
		for (int i = 0; i < nodeCount; i++) {
			Node n = nodes[i];
			for (int j = 0, k; j < n.ins.length; j++)
				if ((k = n.ins[j]) >= 0) {
					Node m = nodes[k];
					m.outs[m.usedOuts++] = i | j << 24;
				}
		}
		loaded = true;
	}

	@Override
	public SignalError run(NodeState a, NodeState state) {
		new MacroState(state, this);
		return null;
	}

}
