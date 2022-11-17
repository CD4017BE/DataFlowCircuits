package cd4017be.compiler;

import java.util.*;

import cd4017be.dfc.editor.Block;
import cd4017be.dfc.lang.BlockDef;

/**
 * 
 * @author CD4017BE */
public class MutableMacro extends Macro {

	public MacroState state;
	public Block[] blocks;
	Block curBlock;
	int[] free;
	int freeCount;

	public MutableMacro(BlockDef def) { 
		super(def);
		this.blocks = new Block[nodes.length];
		this.free = new int[16];
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

	public void addBlock(Block block) {
		curBlock = block;
		int[] io = block.def.content.assemble(this, block.text());
		for (int i = block.def.outCount - 1; i >= 0; i--)
			block.io[i].setNode(io[i]);
		for (int i = block.def.inCount - 1, j = i + block.def.outCount; i >= 0; i--, j--)
			block.nodesIn[i] = io[j];
		curBlock = null;
	}

	public void removeBlock(Block block) {
		Arrays.fill(block.nodesIn, -1);
		for (int i = 0; i < block.def.outCount; i++)
			block.io[i].setNode(-1);
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
