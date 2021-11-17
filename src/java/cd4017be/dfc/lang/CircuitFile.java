package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.BlockDef.OUT_ID;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.zip.DataFormatException;

import cd4017be.dfc.editor.*;
import cd4017be.util.*;
import cd4017be.util.ExtOutputStream.IDTable;

/**File format: <pre>
 * VI defCount * {
 *   B nameLen * {
 *     B utf8byte
 *   }
 * }
 * VI (argCount-1) * {
 *   B argLen * {
 *     B utf8byte
 *   }
 * }
 * VI blockCount * {
 *   I(defCount) def
 *   I(argCount) arg
 *   B x, B y
 *   B ioCount * {
 *     I(blockCount) source
 *     B traceCount * {
 *       B x, B y
 *     }
 *   }
 * } </pre>
 * @author CD4017BE */
public class CircuitFile {

	/** input indices for each block */
	public final int[][] blocks;
	/** definition of each block */
	public final BlockDef[] defs;
	/** argument of each block (may be null) */
	public final String[] args;
	/** index of the output node in {@link #blocks} or -1 if non-existent */
	public final int out;

	public Node[] nodes;

	/**Loads a program from source file.
	 * @param dis source file
	 * @param reg instruction registry
	 * @throws IOException
	 * @throws DataFormatException */
	public CircuitFile(ExtInputStream dis, Function<String, BlockDef> reg)
	throws IOException, DataFormatException {
		BlockDef[] defIdx = dis.readArray(BlockDef[]::new, is -> reg.apply(is.readSmallUTF()));
		String[] argIdx = dis.readArray(String[]::new, ExtInputStream::readSmallUTF);
		int nb = dis.readVarInt(), out = -1;
		this.blocks = new int[nb][];
		this.defs = new BlockDef[nb];
		this.args = new String[nb];
		for (int i = 0; i < nb; i++) {
			BlockDef def = defs[i] = dis.readElement(defIdx);
			if (OUT_ID.equals(def.name)) out = i;
			args[i] = dis.readElement(argIdx);
			dis.readShort(); //skip position
			int ni = dis.readUnsignedByte();
			int[] block = blocks[i] = new int[ni];
			for (int j = 0; j < ni; j++) {
				block[j] = dis.readInt(nb) - 1;
				dis.skipNBytes(dis.readUnsignedByte() * 2); //skip traces
			}
		}
		this.out = out;
	}

	/**Converts an editor's block graph into the node representation needed for compilation.
	 * @param blocks block graph */
	public CircuitFile(IndexedSet<Block> blocks, int start) {
		int nb = blocks.size() - start, out = -1;
		this.blocks = new int[nb][];
		this.defs = new BlockDef[nb];
		this.args = new String[nb];
		for (int i = 0; i < nb; i++) {
			Block block = blocks.get(i + start);
			if (OUT_ID.equals(block.id())) out = i;
			defs[i] = block.def;
			args[i] = block.data;
			int ni = block.io.length - 1;
			int[] data = this.blocks[i] = new int[ni];
			for (int j = 0; j < ni; j++) {
				Trace tr = block.io[j + 1];
				while(tr != null && tr.pin > 0) tr = tr.from;
				data[j] = tr != null && tr.block != null ? tr.block.getIdx() - start : -1;
			}
		}
		this.out = out;
	}

	public Signal eval1(int from, int i) throws SignalError {
		Signal[] s = eval(from, i);
		if (s.length == 0) throw new SignalError(from, i, "missing input");
		return s[0];
	}

	public Signal[] eval(int from, int i) throws SignalError {
		int j = blocks[from][i];
		if (j < 0) return Signal.EMPTY;
		Node n = nodes[j];
		if (n == null) {
			BlockDef def = defs[j];
			nodes[j] = n = new Node(def, blocks[j].length);
			def.eval.eval(this, j, n);
		}
		nodes[from].in[i] = n;
		return n.out;
	}

	public void typeCheck() throws SignalError {
		GlobalVar.clear();
		Type.clearTypeIndex();
		if (out < 0) throw new SignalError(-1, -1, "missing root node");
		nodes = new Node[blocks.length];
		BlockDef def = defs[out];
		def.eval.eval(this, out, nodes[out] = new Node(def, blocks[out].length));
	}

	/**Writes an editor's block graph to a source file.
	 * @param blocks block graph
	 * @param out source file
	 * @throws IOException */
	public static void save(IndexedSet<Block> blocks, int start, ExtOutputStream out)
	throws IOException {
		List<Block> blockList = blocks.subList(start, blocks.size());
		IDTable<Block, String> defs = out.new IDTable<>(
			blockList, Block::id, ExtOutputStream::writeSmallUTF
		);
		IDTable<Block, String> args = out.new IDTable<>(
			blockList, b -> b.data, ExtOutputStream::writeSmallUTF
		);
		int l = blockList.size();
		out.writeVarInt(l);
		for (Block block : blockList) {
			defs.writeId(block);
			args.writeId(block);
			out.writeByte(block.x);
			out.writeByte(block.y);
			out.writeByte(block.io.length - 1);
			for (int i = 1; i < block.io.length; i++) {
				int n = 0, src = -1;
				boolean tracing = true;
				for (Trace t0 = block.io[i], t = t0.from; t != null; t0 = t, t = t.from) {
					if (tracing) {
						n++;
						if (t.pin >= 0 || t.to != t0)
							tracing = false;
					}
					if (t.pin == 0) {
						src = t.block.getIdx() - start;
						break;
					}
				}
				out.writeInt(src + 1, l);
				if (n > 255) n = 255;
				out.writeByte(n);
				for (Trace t = block.io[i].from; n > 0; t = t.from, n--) {
					out.writeByte(t.x());
					out.writeByte(t.y());
				}
			}
		}
	}

	/**Loads a program from a source file into an editor's block graph.
	 * @param c the block graph editor
	 * @param dis source file
	 * @throws IOException
	 * @throws DataFormatException */
	public static void load(Circuit c, ExtInputStream dis)
	throws IOException, DataFormatException {
		String[] ids = dis.readArray(String[]::new, ExtInputStream::readSmallUTF);
		String[] args = dis.readArray(String[]::new, ExtInputStream::readSmallUTF);
		for (int n = dis.readVarInt(), i = 0; i < n; i++) {
			Block block = new Block(dis.readElement(ids), c);
			block.data = dis.readElement(args);
			int x = dis.readByte(), y = dis.readByte();
			int m = dis.readUnsignedByte();
			if (m != block.io.length - 1)
				throw new DataFormatException("pin count" + m + " doesn't match type " + block.def);
			for (int j = 1; j <= m; j++) {
				Trace t0 = block.io[j];
				dis.readInt(n);
				for (int l = dis.readUnsignedByte(); l > 0; l--) {
					Trace t = new Trace(c).pos(dis.readByte(), dis.readByte());
					t0.connect(t);
					t0 = t.place();
				}
			}
			block.pos(x, y).place();
		}
	}

}
