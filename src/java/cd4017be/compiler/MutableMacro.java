package cd4017be.compiler;

import java.util.*;

import cd4017be.compiler.instr.ConstList;
import cd4017be.dfc.editor.*;

/**
 * 
 * @author CD4017BE */
public class MutableMacro extends Macro {

	public MacroState state;
	public CodeBlock[] blocks;
	CodeBlock curBlock;
	int[] free;
	int freeCount;

	public MutableMacro(BlockDef def) { 
		super(def);
		this.blocks = new CodeBlock[nodes.length];
		this.free = new int[16];
	}

	@Override
	public void load() {
		clear();
		//create output and input nodes
		String[] outs = def.outs;
		Node out = addNode(OUTPUT, null, outs.length);
		for (int i = 0; i < ins(); i++)
			links.put(def.ins[i], addNode(INPUT, i, 0));
		if (!(def.assembler instanceof ConstList))
			for (int i = 0; i < outs.length; i++) {
				Node n = addIONode(outs[i]);
				out.ins[i] = n.idx;
				n.addOut(out.idx(i));
			}
		loaded = true;
	}

	@Override
	public Node addNode(NodeOperator op, Object data, int ins) {
		int i;
		if (freeCount > 0) i = free[--freeCount];
		else {
			i = nodeCount++;
			if (i >= nodes.length) {
				nodes = Arrays.copyOf(nodes, i * 2);
				blocks = Arrays.copyOf(blocks, i * 2);
			}
			if (state != null) {
				NodeState[] states = state.states;
				if (i >= states.length)
					state.states = Arrays.copyOf(states, states.length * 2);
			}
		}
		blocks[i] = curBlock;
		return nodes[i] = new Node(op, data, ins, i);
	}

	@Override
	public Node addIONode(String name) {
		Node n = super.addIONode(name);
		blocks[n.idx] = null;
		//TODO for ConstList connect all I/O nodes to output
		return n;
	}

	public void connect(int src, int dst) {
		if (dst == -1) return;
		Node dstN = nodes[dst & 0xffffff];
		int i = dstN.ins[dst >>> 24];
		if (i == src) return;
		if (i >= 0) nodes[i].remOut(dst);
		dstN.ins[dst >>> 24] = src;
		if (src >= 0) {
			nodes[src].addOut(dst);
			if (state != null) {
				NodeState ns = state.states[dst & 0xffffff];
				if (ns != null && ns.scope != null) {
					ns.update |= 1L << (dst >>> 24);
					state.updateScope(src);
				}
			}
		} else if (state != null) {
			NodeState ns = state.states[dst & 0xffffff];
			if (ns != null) ns.updateIn(dst >> 24);
		}
	}

	public void addBlock(CodeBlock block, CircuitEditor cc) {
		curBlock = block;
		int[] io = block.def.assembler.assemble(this, block.def, block.outs, block.ins(), block.args);
		for (int i = block.outs - 1; i >= 0; i--)
			block.io[i].setNode(io[i], cc);
		for (int j = block.io.length - 1, i = j - block.outs; i >= 0; i--, j--) {
			int idx = block.nodesIn[i] = io[j];
			connect(block.io[j].node(), idx);
		}
		curBlock = null;
	}

	public void removeBlock(CodeBlock block, CircuitEditor cc) {
		for (int i = 0; i < block.outs; i++) {
			Trace out = block.io[i];
			//clear destinations first to ensure the signal trace will ripple flush
			for(Trace tr = out.to; tr != null; tr = tr.adj)
				tr.setNode(-1, cc);
			out.setNode(-1, cc);
		}
		Arrays.fill(block.nodesIn, -1);
		for (int i = 0; i < nodeCount; i++) {
			if (blocks[i] != block) continue;
			Node node = nodes[i];
			if (node == null) continue;
			for (int j = node.ins.length - 1; j >= 0; j--) {
				int k = node.ins[j];
				if (k < 0 || blocks[k] == block) continue;
				nodes[k].remOut(i | j << 24);
			}
			for (int j = node.usedOuts - 1; j >= 0; j--) {
				int k = node.outs[j];
				nodes[k & 0xffffff].ins[k >>> 24] = -1;
			}
			if (state != null) {
				NodeState[] states = state.states;
				NodeState ns = states[i];
				if (ns != null) ns.remove();
				for (int j = node.usedOuts - 1; j >= 0; j--) {
					int k = node.outs[j], k1 = k & 0xffffff;
					if (blocks[k1] != block && (ns = states[k1]) != null)
						ns.updateIn(k >> 24);
				}
			}
			if (freeCount >= free.length) free = Arrays.copyOf(free, free.length * 2);
			free[freeCount++] = i;
			nodes[i] = null;
			blocks[i] = null;
		}
	}

}
