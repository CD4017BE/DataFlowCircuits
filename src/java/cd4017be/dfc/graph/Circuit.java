package cd4017be.dfc.graph;

import java.util.*;
import java.util.regex.Pattern;

import cd4017be.dfc.graph.Macro.Pin;
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
			for (int j = info.def().addOut - 1; j >= 0; j--)
				links.put(info.arguments()[j], info.inputs()[j]);
		}
		if (def.hasArg) {
			String[] args = SEP.split(def.ioNames[def.ioNames.length - 1]);
			for (int i = 0; i < args.length; i++)
				links.put(args[i], 0x2_00_0000 | i);
			this.argRefs = new ArgUse[args.length];
		} else this.argRefs = null;
		for (int i = 0; i < def.inCount; i++)
			links.put(def.ioNames[def.outCount + i], 0x1_00_0000 | i);
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo info = blocks[i]; BlockDef d = info.def();
			String[] args = info.arguments();
			for (int j = 0; j < args.length; j++) {
				String arg = args[j];
				int id = links.getOrDefault(arg, -1);
				if (id >> 24 == 2)
					argRefs[id & 0xffff] = new ArgUse(argRefs[id & 0xffff], i, j);
			}
			if (d.addIn > 0) {
				int[] ins = Arrays.copyOf(info.inputs(), d.addIn + d.inCount);
				for (int j = 0; j < d.addIn; j++)
					ins[j + d.inCount] = links.getOrDefault(args[j], -1);
				blocks[i] = new BlockInfo(d, args, ins);
			}
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
			if (argRefs != null) {
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
			}
		} else me = new MacroExpansion(node);
		for (int i = 0; i < node.out.length; i++) {
			Pin pin = me.getOutput(i, c);
			node.replaceOut(i, pin.node(), pin.pin(), c);
		}
		if ((node.updating & -2) != 0)
			for (Node n : me.nodes)
				if (n != null)
					n.disconnectExtraInputs(c);
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
			if (argRefs != null)
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

		private Pin getNode(int idx, Context c) {
			if (idx >= 0x2_00_0000) {
				idx = links.getOrDefault(args[idx & 0xffff], -1);
				if (idx >= 0x2_00_0000) return Node.NULL_PIN;
			}
			if (idx >= 0x1_00_0000) return parent.connectIn(idx & 0xffff, c).srcPin();
			if (idx < 0) return Node.NULL_PIN;
			int ni = idx & 0xffff, pi = idx >>> 16;
			Node n = nodes[ni];
			if (n != null)
				return n.data instanceof Macro m
					? m.getOutput(pi, c)
					: new Pin(n, pi);
			BlockInfo block = blocks[ni];
			n = new Node(this, ni, block.def());
			c.updateNode(nodes[ni] = n, 0);
			return new Pin(n, pi);
		}

		@Override
		public Pin getOutput(int i, Context c) {
			return getNode(out[i], c);
		}

		@Override
		public void connectInput(Node n, int i, Context c) {
			Pin pin = getNode(blocks[n.idx].inputs()[i], c);
			if (pin.node().data instanceof Macro m)
				pin = m.getOutput(pin.pin(), c);
			n.connect(i + n.out.length, pin.node(), pin.pin(), c);
		}

		@Override
		public String[] arguments(Node n, int min) {
			String[] arr = blocks[n.idx].arguments(), res;
			int l = arr.length, l1 = argRefs == null ? 0 : argRefs.length;
			if (l > 0 && extraArgs > 0 && links.getOrDefault(arr[l - 1], -1) == l1 + 0x1_ffffff) {
				int m = extraArgs + l;
				if (m >= min) res = new String[m];
				else Arrays.fill(res = new String[min], m, min, "");
				System.arraycopy(args, l1, res, l, extraArgs);
			} else if (l >= min) res = new String[l];
			else Arrays.fill(res = new String[min], l, min, "");
			for (int i = 0; i < l; i++) {
				int id = links.getOrDefault(arr[i], -1);
				res[i] = id >= 0x2_00_0000 ? args[id & 0xffff] : arr[i];
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
