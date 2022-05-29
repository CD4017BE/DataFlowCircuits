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
	final int argCount;
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
			this.argCount = args.length;
		} else this.argCount = 0;
		for (int i = 0; i < def.inCount; i++)
			links.put(def.ioNames[def.outCount + i], 0x10000 | i);
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo info = blocks[i]; BlockDef d = info.def();
			if (!BlockDef.IN_ID.equals(d.name)) continue;
			String[] args = info.arguments();
			blocks[i] = new BlockInfo(d, args, new int[] {links.getOrDefault(args[0], -1)});
		}
		this.out = new int[def.outCount];
		for (int i = 0; i < out.length; i++)
			out[i] = links.getOrDefault(def.ioNames[i], -1);
	}

	@Override
	public void update(Node node, Context c) throws SignalError {
		MacroExpansion me = node.data instanceof MacroExpansion m
			? m : new MacroExpansion(node);
		node.replaceWith(me.getOutput(c), c);
	}

	public class MacroExpansion implements Macro {

		final Node[] nodes;
		final String[] args;
		final Node parent;
		final int extraArgs;

		public MacroExpansion(Node parent) {
			this.nodes = new Node[blocks.length];
			this.args = parent.arguments(argCount);
			this.extraArgs = args.length - argCount;
			this.parent = parent;
			parent.data = this;
		}

		private Node getNode(int idx, Context c) {
			if (idx >= 0x20000) {
				idx = links.getOrDefault(args[idx & 0xffff], -1);
				if (idx >= 0x20000) return Node.NULL;
			}
			if (idx >= 0x10000) return parent.getInput(idx & 0xffff, c);
			if (idx < 0) return Node.NULL;
			Node n = nodes[idx];
			if (n != null) return n;
			BlockInfo block = blocks[idx];
			n = new Node(this, idx, block.def(), block.inputs().length);
			c.updateNode(nodes[idx] = n, 0);
			return n;
		}

		@Override
		public Node getOutput(Context c) {
			return getNode(out[0], c);
		}

		@Override
		public void connectInput(Node n, int i, Context c) {
			n.connect(i + 1, getNode(blocks[n.idx].inputs()[i], c), c);
		}

		@Override
		public String[] arguments(Node n, int min) {
			String[] arr = blocks[n.idx].arguments(), res;
			int l = arr.length;
			if (l > 0 && extraArgs > 0 && links.getOrDefault(arr[l - 1], -1) == argCount + 0x1ffff) {
				int m = extraArgs + l;
				if (m >= min) res = new String[m];
				else Arrays.fill(res = new String[min], m, min, "");
				System.arraycopy(args, argCount, res, l, extraArgs);
			} else if (l >= min) res = new String[l];
			else Arrays.fill(res = new String[min], l, min, "");
			for (int i = 0; i < l; i++) {
				int id = links.getOrDefault(arr[i], -1);
				res[i] = id >= 0x20000 ? args[id & 0xffff] : arr[i];
			}
			return res;
		}

	}

}
