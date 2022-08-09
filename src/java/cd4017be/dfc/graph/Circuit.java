package cd4017be.dfc.graph;

import java.util.*;
import java.util.regex.Pattern;

import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.SignalError;

/**
 * 
 * @author CD4017BE */
public class Circuit implements Behavior {

	public static final Pattern SEP = Pattern.compile("\\s*,\\s*");

	final BlockDef def;
	final BlockInfo[] blocks;
	/**-1 = null, 0x00000...0x0ffff = blocks,
	 * 0x10000...0x1ffff = inputs, 0x20000...0x2ffff = arguments */
	final HashMap<String, Integer> links = new HashMap<>();
	final ArgUse[] argRefs;
	final int[] out;

	public Circuit(BlockDef def, BlockInfo[] blocks) {
		this.def = def;
		this.blocks = blocks;
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo info = blocks[i];
			if (BlockDef.OUT_ID.equals(info.def().name))
				links.put(info.arguments()[0], info.inputs()[0]);
		}
		if (def.hasArg) {
			String[] args = SEP.split(def.ioNames[def.ioNames.length - 1]);
			for (int i = 0; i < args.length; i++)
				links.put(args[i], 0x20000 | i);
			this.argRefs = new ArgUse[args.length];
		} else this.argRefs = null;
		for (int i = 0; i < def.inCount; i++)
			links.put(def.ioNames[def.outCount + i], 0x10000 | i);
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo info = blocks[i]; BlockDef d = info.def();
			String[] args = info.arguments();
			for (int j = 0; j < args.length; j++) {
				String arg = args[j];
				int id = links.getOrDefault(arg, -1);
				if (id >> 16 == 2)
					argRefs[id & 0xffff] = new ArgUse(argRefs[id & 0xffff], i, j);
			}
			if (BlockDef.IN_ID.equals(d.name))
				blocks[i] = new BlockInfo(d, args, new int[] {links.getOrDefault(args[0], -1)});
		}
		this.out = new int[def.outCount];
		for (int i = 0; i < out.length; i++)
			out[i] = links.getOrDefault(def.ioNames[i], -1);
	}

	@Override
	public void update(Node node, Context c) throws SignalError {
		MacroExpansion me;
		if (node.data instanceof MacroExpansion m) {
			me = m;
			String[] args0 = m.args, args1 = node.arguments(argRefs.length);
			int l = argRefs.length - 1;
			for (int i = 0; i < l; i++)
				if (!args0[i].equals(args1[i]))
					m.updateArgRefs(i, c);
			if (args0.length != args1.length)
				m.updateArgRefs(l, c);
			else for (int i = l; i < args0.length; i++)
				if (!args0[i].equals(args1[i])) {
					m.updateArgRefs(l, c);
					break;
				}
			m.setArgs(args1);
		} else me = new MacroExpansion(node);
		node.replaceWith(me.getOutput(c), c);
		if ((node.updating & -2) != 0)
			for (Node n : me.nodes)
				if (n != null && BlockDef.IN_ID.equals(n.def.name))
					n.connect(0, null, c);
	}

	public class MacroExpansion implements Macro {

		final Node[] nodes;
		final Node parent;
		String[] args;
		int extraArgs;

		public MacroExpansion(Node parent) {
			this.nodes = new Node[blocks.length];
			this.parent = parent;
			parent.data = this;
			this.setArgs(parent.arguments(argRefs.length));
		}

		private void setArgs(String[] args) {
			this.args = args;
			this.extraArgs = args.length - argRefs.length;
		}

		private void updateArgRefs(int i, Context c) {
			for (ArgUse u = argRefs[i]; u != null; u = u.next) {
				Node n = nodes[u.block];
				if (n != null) c.updateNode(n, 0);
			}
		}

		private Node getNode(int idx, Context c) {
			if (idx >= 0x20000) {
				idx = links.getOrDefault(args[idx & 0xffff], -1);
				if (idx >= 0x20000) return Node.NULL;
			}
			if (idx >= 0x10000) return parent.getInput(idx & 0xffff, c);
			if (idx < 0) return Node.NULL;
			Node n = nodes[idx];
			if (n != null)
				return n.data instanceof Macro m ? m.getOutput(c) : n;
			BlockInfo block = blocks[idx];
			n = new Node(this, idx, block.def(), block.inputs().length + 1);
			c.updateNode(nodes[idx] = n, 0);
			return n;
		}

		@Override
		public Node getOutput(Context c) {
			return getNode(out[0], c);
		}

		@Override
		public void connectInput(Node n, int i, Context c) {
			Node node = getNode(blocks[n.idx].inputs()[i], c);
			if (node.data instanceof Macro m)
				node = m.getOutput(c);
			n.connect(i, node, c);
		}

		@Override
		public String[] arguments(Node n, int min) {
			String[] arr = blocks[n.idx].arguments(), res;
			int l = arr.length, l1 = argRefs.length;
			if (l > 0 && extraArgs > 0 && links.getOrDefault(arr[l - 1], -1) == l1 + 0x1ffff) {
				int m = extraArgs + l;
				if (m >= min) res = new String[m];
				else Arrays.fill(res = new String[min], m, min, "");
				System.arraycopy(args, l1, res, l, extraArgs);
			} else if (l >= min) res = new String[l];
			else Arrays.fill(res = new String[min], l, min, "");
			for (int i = 0; i < l; i++) {
				int id = links.getOrDefault(arr[i], -1);
				res[i] = id >= 0x20000 ? args[id & 0xffff] : arr[i];
			}
			return res;
		}

		@Override
		public Node parent() {
			return parent;
		}

	}

	static record ArgUse(ArgUse next, int block, int arg) {}

}
