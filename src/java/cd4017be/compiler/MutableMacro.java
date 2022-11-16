package cd4017be.compiler;

import java.util.*;

import cd4017be.compiler.ops.StandardOps;
import cd4017be.dfc.editor.Block;
import cd4017be.dfc.editor.Trace;
import cd4017be.dfc.lang.BlockDef;

/**
 * 
 * @author CD4017BE */
public class MutableMacro extends Macro {

	public MacroState state;
	int[] free;
	int freeCount;

	public MutableMacro(BlockDef def) { 
		super(def);
		this.free = new int[16];
	}

	@Override
	protected int nextId() {
		if (freeCount > 0) return free[--freeCount];
		int i = super.nextId();
		if (state != null) {
			NodeState[] states = state.states;
			if (i >= states.length)
				state.states = Arrays.copyOf(states, states.length * 2);
		}
		return i;
	}

	public void connect(int src, int dst) {
		if (dst == -1) return;
		Node dstN = nodes[dst & 0xffffff];
		int i = dstN.ins[dst >>> 24];
		if (i == src) return;
		if (i >= 0) nodes[i].remOut(dst);
		dstN.ins[dst >>> 24] = src;
		if (src >= 0) nodes[src].addOut(dst);
		//TODO updates
	}

	public void removeNode(Node node) {
		int i = node.idx;
		if (freeCount >= free.length) free = Arrays.copyOf(free, free.length * 2);
		free[freeCount++] = i;
		nodes[i] = null;
		if (state != null) {
			NodeState ns = state.states[i];
			state.states[i] = null;
			if (ns != null) ns.update = 1; //prevent scheduled updates
		}
	}

	public void addBlock(Block block) {
		int[] io = block.def.content.assemble(this, block.text());
		for (int i = block.def.outCount - 1; i >= 0; i--)
			block.io[i].setNode(io[i]);
		for (int i = block.def.inCount - 1, j = i + block.def.outCount; i >= 0; i--, j--)
			block.nodesIn[i] = io[j];
	}

	public void removeBlock(Block block) {
		int n = block.def.outCount;
		for (int i = block.def.inCount - 1; i >= 0; i--) {
			connect(-1, block.nodesIn[i]);
			block.nodesIn[i] = -1;
		}
		int[] erase = new int[n];
		for (int i = 0; i < n; i++) {
			Trace t = block.io[i];
			int j = t.node();
			t.setNode(-1);
			if (j >= 0) {
				Node node = nodes[j];
				if (
					node.op == StandardOps.INPUT && in((int)node.data) == node
					|| node.op == StandardOps.PASS && links.get(node.data) == node
				) j = -1;
			}
			erase[i] = j;
		}
		while (--n >= 0) {
			int e = erase[n];
			if (e < 0) continue;
			Node node = nodes[e];
			if (node == null) continue;
			removeNode(node);
			int l = node.ins.length;
			if (n + l > erase.length)
				erase = Arrays.copyOf(erase, Math.max(n + l, l * 2));
			System.arraycopy(node.ins, 0, erase, n, l);
			n += l;
		}
	}

}
