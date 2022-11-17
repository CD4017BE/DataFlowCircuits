package cd4017be.compiler;

import static cd4017be.compiler.NodeOperator.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import cd4017be.dfc.graph.BlockInfo;
import cd4017be.dfc.lang.*;

/**
 * @author CD4017BE */
public class Macro implements NodeAssembler, VirtualMethod {

	int nodeCount;
	public final BlockDef def;
	HashMap<String, Node> links;
	Node[] nodes;
	private boolean loaded;

	public Macro(BlockDef def) {
		this.def = def;
		this.nodes = new Node[Integer.highestOneBit(def.ios() - 1 << 1)];
		this.links = new HashMap<>();
	}

	public void clear() {
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
		return def.inCount;
	}

	public Node in(int i) {
		return nodes[i + 1];
	}

	public Node out() {
		return nodes[0];
	}

	@Override
	public int[] assemble(Macro macro, String arg) {
		int ins = def.inCount, outs = def.outCount;
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
		if (!loaded) load(context.reg);
		return this;
	}

	public void load(BlockRegistry reg) {
		//load data
		BlockInfo[] blocks;
		try (CircuitFile file = reg.openFile(def.name, false)) {
			blocks = file.readCircuit(reg);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		clear();
		//create output and input nodes
		Node out = addNode(OUTPUT, null, def.outCount);
		for (int i = 0, j = def.outCount; i < def.inCount; i++, j++)
			links.put(def.ioNames[j], addNode(INPUT, i, 0));
		//assemble block nodes
		int[][] arr = new int[blocks.length][];
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo info = blocks[i];
			arr[i] = info.def().content.assemble(this, info.arguments()[0]);
		}
		//connect macro outputs
		for (int i = 0; i < def.outCount; i++) {
			Node n = links.get(def.ioNames[i]);
			out.ins[i] = n == null ? -1 : n.idx;
		}
		//connect blocks
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo info = blocks[i];
			int o = info.def().outCount;
			int[] idxs = arr[i];
			int[] ins = info.inputs();
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
	public SignalError run(Signal a, NodeState state) {
		new MacroState(state, this);
		return null;
	}

}
