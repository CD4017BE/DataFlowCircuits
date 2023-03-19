package cd4017be.dfc.lang.builders;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.Node.Vertex;
import cd4017be.dfc.lang.instructions.PackIns;
import cd4017be.dfc.lang.instructions.UnpackIns;
import cd4017be.util.IndexedSet;


/**
 * 
 * @author CD4017BE */
public class Macro extends NodeContext implements NodeAssembler {

	private final int[] argUsers;
	private IndexedSet<BlockDesc> blocks;
	private NodeContext parent;
	private String[] extArgs;

	public Macro(BlockDef def) {
		super(def, false);
		this.argUsers = new int[def.args.length];
		Arrays.fill(argUsers, -1);
	}

	private void ensureLoaded() throws IOException {
		if (blocks != null) return;
		synchronized(def) {
			if (blocks != null) return;
			blocks = CircuitFile.readCircuit(CircuitFile.readBlock(def), def.module);
			args: for (int i = 0; i < argUsers.length; i++) {
				String name = def.args[i];
				for (int j = 0; j < blocks.size(); j++) {
					BlockDesc block = blocks.get(j);
					for (int k = 0; k < block.args.length; k++)
						if (name.equals(block.args[k])) {
							argUsers[i] = j | k << 16;
							continue args;
						}
				}
				argUsers[i] = -1;
			}
		}
	}

	@Override
	public String[] args(BlockDesc block) {
		String[] args = block.args, names = def.args;
		if (names.length == 0) return args;
		int[] idx = null;
		args: for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			for (int j = 0; j < names.length; j++)
				if (names[j].equals(arg)) {
					if (idx == null)
						Arrays.fill(idx = new int[args.length], -1);
					idx[i] = j;
					continue args;
				}
		}
		if (idx == null) return args;
		String[] res;
		int l, e = names.length - 1;
		if (idx[idx.length - 1] == e && extArgs.length != names.length) {
			l = args.length - 1;
			int n = max(extArgs.length - e, 0);
			res = new String[l + n];
			if (n > 0) System.arraycopy(extArgs, names.length - 1, res, l, n);
		} else res = new String[l = args.length];
		for (int i = 0; i < l; i++) {
			int j = idx[i];
			res[i] = j < 0 ? args[i] : j < extArgs.length ? extArgs[j] : "";
		}
		return res;
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
		try {
			ensureLoaded();
		} catch(IOException e) {
			throw new SignalError(idx, "can't load macro: " + e.getMessage(), e);
		}
		if (parent != null) throw new SignalError(idx, "illegal recursive macro");
		try {
			this.parent = context;
			this.extArgs = context.args(block);
			build(blocks, false);
			String[] names;
			int j = (names = def.ins).length;
			for (int i = 0, l = min(j, block.ins()); i < l; i++) {
				Node node = links.get(names[i]);
				block.ins[i] = node == null ? null : node.in[0];
			}
			if (block.ins() > j--) {
				Node pack = new Node(new PackIns(), Node.INSTR, block.ins() - j, idx);
				block.ins[j].connect(pack);
				for (Vertex v : pack.in) block.ins[j++] = v;
			}
			j = (names = def.outs).length;
			for (int i = 0, l = min(j, block.outs()); i < l; i++)
				block.outs[i] = links.get(names[i]);
			if (block.outs() > j--) {
				Node out = block.outs[j];
				for (int i = 0; j < block.outs(); j++, i++)
					block.outs[j] = out == null ? null : UnpackIns.node(i, out, idx);
			}
			j = (names = def.args).length;
			for (int i = 0, l = block.args.length > j ? j - 1 : block.args.length; i < l; i++) {
				Node link = links.get(names[i]);
				if (link == null);
				else if (link.in[0].from() == null)
					link.in[0].connect(context.getIO(block.args[i]));
				else
					context.getIO(block.args[i]).in[0].connect(link);
			}
			if (block.args.length > j--) {
				Node link = links.get(names[j]);
				if (link == null);
				else if (link.in[0].from() == null) {
					Node pack = new Node(new PackIns(), Node.INSTR, block.args.length - j, idx);
					link.in[0].connect(pack);
					for (Vertex v : pack.in)
						v.connect(context.getIO(block.args[j++]));
				} else {
					for (int i = 0; j < block.args.length; j++, i++)
						context.getIO(block.args[j]).in[0].connect(UnpackIns.node(i, link, idx));
				}
			}
		} finally {
			parent = null;
			extArgs = null;
			links.clear();
		}
	}

	@Override
	public void
	getAutoCompletions(BlockDesc block, int arg, ArrayList<String> list, NodeContext context) {
		try {
			ensureLoaded();
		} catch(IOException e) {
			return;
		}
		if ((arg = min(arg, argUsers.length - 1)) < 0 || parent != null) return;
		try {
			this.parent = context;
			int i = argUsers[arg];
			if (i >= 0) {
				block = blocks.get(i & 0xffff);
				block.def.assembler.getAutoCompletions(block, i >> 16, list, context);
			}
		} finally {
			this.parent = null;
		}
	}

}
