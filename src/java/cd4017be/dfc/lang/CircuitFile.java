package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.BlockDef.OUT_ID;
import static java.lang.Integer.numberOfLeadingZeros;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.zip.DataFormatException;

import cd4017be.dfc.editor.*;
import cd4017be.dfc.lang.HeaderParser.CDecl;
import cd4017be.dfc.lang.type.Types;
import cd4017be.util.*;
import cd4017be.util.ExtOutputStream.IDTable;

/**
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
	public HashMap<String, CDecl> include = new HashMap<>();
	public HashMap<String, String> macros = new HashMap<>();

	/**Loads a program from source file.
	 * @param dis source file
	 * @param reg instruction registry
	 * @throws IOException
	 * @throws DataFormatException */
	public CircuitFile(ExtInputStream dis, Function<String, BlockDef> reg)
	throws IOException, DataFormatException {
		GlobalVar.clear();
		Types.clear();
		int pb = (dis.read() & 3) + 1;
		BlockDef[] defIdx = loadDefs(dis, reg);
		String[] argIdx = dis.readArray(String[]::new, ExtInputStream::readSmallUTF);
		int nb = dis.readVarInt(), out = -1;
		this.blocks = new int[nb][];
		this.defs = new BlockDef[nb];
		this.args = new String[nb];
		for (int i = 0; i < nb; i++) {
			BlockDef def = defs[i] = dis.readElement(defIdx);
			if (OUT_ID.equals(def.name)) out = i;
			args[i] = dis.readElement(argIdx);
			dis.skipNBytes(pb); //skip position
			int ni = def.ios() - 1;
			int[] block = blocks[i] = new int[ni];
			for (int j = 0; j < def.pins; j++) {
				if (j < ni) block[j] = dis.readInt(nb) - 1;
				dis.skipNBytes(dis.readVarInt() * pb); //skip traces
			}
			for (int j = def.pins; j < ni; j++) block[j] = -1;
		}
		this.out = out;
	}

	/**Converts an editor's block graph into the node representation needed for compilation.
	 * @param blocks block graph */
	public CircuitFile(IndexedSet<Block> blocks) {
		GlobalVar.clear();
		Types.clear();
		int nb = blocks.size(), out = -1;
		this.blocks = new int[nb][];
		this.defs = new BlockDef[nb];
		this.args = new String[nb];
		for (int i = 0; i < nb; i++) {
			Block block = blocks.get(i);
			if (OUT_ID.equals(block.id())) out = i;
			defs[i] = block.def;
			args[i] = block.data;
			int ni = block.io.length - 1;
			int[] data = this.blocks[i] = new int[ni];
			for (int j = 0; j < ni; j++) {
				Trace tr = block.io[j + 1];
				while(tr != null && tr.pin > 0) tr = tr.from;
				data[j] = tr != null && tr.block != null ? tr.block.getIdx() : -1;
			}
		}
		this.out = out;
	}

	public CircuitFile setDefinitions(HeaderParser hp) throws IOException {
		hp.getMacros(macros);
		hp.getDeclarations(include);
		return this;
	}

	public Signal eval(int from, int i) throws SignalError {
		int j = blocks[from][i];
		if (j < 0) return Signal.NULL;
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
		if (out < 0) throw new SignalError(-1, -1, "missing root node");
		nodes = new Node[blocks.length];
		BlockDef def = defs[out];
		def.eval.eval(this, out, nodes[out] = new Node(def, blocks[out].length));
	}

	private static BlockDef[] loadDefs(ExtInputStream dis, Function<String, BlockDef> reg)
	throws IOException, DataFormatException {
		return dis.readArray(BlockDef[]::new, is -> {
			int n = is.read() + 1;
			String name = is.readSmallUTF();
			BlockDef def = reg.apply(name);
			if (def == null)
				throw new DataFormatException("type '" + name + "' is undefined");
			if (def.pins != n) def = def.withPins(n);
			return def;
		});
	}

	private static int[] scanTraces(List<Block> blocks) {
		int iol = 0;
		for (Block block : blocks) iol += block.io.length - 1;
		int[] io = new int[iol * 2 + 4];
		int minX = Short.MAX_VALUE, maxX = Short.MIN_VALUE, minY = minX, maxY = maxX;
		iol = 4;
		for (Block block : blocks) {
			maxX = max(maxX, block.x);
			minX = min(minX, block.x);
			maxY = max(maxY, block.y);
			minY = min(minY, block.y);
			for (int i = 1; i < block.io.length; i++) {
				int n = 0, src = -1;
				boolean tracing = true;
				for (Trace t0 = block.io[i], t = t0.from; t != null; t0 = t, t = t.from) {
					if (tracing) {
						maxX = max(maxX, t0.x());
						minX = min(minX, t0.x());
						maxY = max(maxY, t0.y());
						minY = min(minY, t0.y());
						n++;
						if (t.pin >= 0 || t.to != t0)
							tracing = false;
					}
					if (t.pin == 0) {
						src = t.block.getIdx();
						break;
					}
				}
				io[iol++] = src;
				io[iol++] = n;
			}
		}
		io[0] = minX + maxX + 1 >> 1;
		io[1] = minY + maxY + 1 >> 1;
		io[2] = max(1, 32 - numberOfLeadingZeros(maxX - minX));
		io[3] = max(1, 32 - numberOfLeadingZeros(maxY - minY));
		return io;
	}

	private static void writePos(
		ExtOutputStream out, int x, int y, int shift, int n
	) throws IOException {
		for(int v = x & (1 << shift) - 1 | y << shift; n > 0; n--, v >>= 8)
			out.write(v);
	}

	/**Writes an editor's block graph to a source file.
	 * @param blocks block graph
	 * @param out source file
	 * @throws IOException */
	public static void save(IndexedSet<Block> blocks, ExtOutputStream out)
	throws IOException {
		int[] traces = scanTraces(blocks);
		int refX = traces[0], refY = traces[1], shift = traces[2];
		int size = shift + traces[3] + 7 >> 3;
		out.writeByte(size - 1 | shift << 4);
		IDTable<Block, BlockDef> defs = out.new IDTable<>(
			blocks, b -> b.def, (os, d) -> {
				os.writeByte(d.ios() - 1);
				os.writeSmallUTF(d.name);
			}
		);
		IDTable<Block, String> args = out.new IDTable<>(
			blocks, b -> b.data, ExtOutputStream::writeSmallUTF
		);
		int l = blocks.size(), tri = 4;
		out.writeVarInt(l);
		for (Block block : blocks) {
			defs.writeId(block);
			args.writeId(block);
			writePos(out, block.x - refX, block.y - refY, shift, size);
			for (int i = 1; i < block.io.length; i++) {
				out.writeInt(traces[tri++] + 1, l);
				int n = traces[tri++];
				out.writeVarInt(n);
				for (Trace t = block.io[i].from; n > 0; t = t.from, n--)
					writePos(out, t.x() - refX, t.y() - refY, shift, size);
			}
		}
	}

	private static int[] readPos(ExtInputStream dis, int shift, int n) throws IOException {
		int v = 0, s = n * 8;
		for (int i = 0; i < s; i+=8)
			v |= dis.read() << i;
		int x = v << -shift >> -shift, y = v << -s >> shift - s;
		return new int[] {x, y};
	}

	/**Loads a program from a source file into an editor's block graph.
	 * @param c the block graph editor
	 * @param dis source file
	 * @throws IOException
	 * @throws DataFormatException */
	public static void load(Circuit c, ExtInputStream dis)
	throws IOException, DataFormatException {
		int size = dis.readByte() & 0xff, shift = size >> 4;
		size = (size & 3) + 1;
		BlockDef[] ids = loadDefs(dis, c);
		String[] args = dis.readArray(String[]::new, ExtInputStream::readSmallUTF);
		int n = dis.readVarInt();
		c.reserveBlockBuf(n);
		for (int i = 0; i < n; i++) {
			Block block = new Block(dis.readElement(ids), c);
			block.data = dis.readElement(args);
			int[] p = readPos(dis, shift, size);
			for (int j = 1; j < block.def.pins; j++) {
				Trace t0 = j < block.io.length ? block.io[j] : null;
				dis.readInt(n);
				for (int l = dis.readVarInt(); l > 0; l--) {
					int[] p1 = readPos(dis, shift, size);
					if (t0 == null) continue;
					Trace t = new Trace(c).pos(p1[0], p1[1]);
					t0.connect(t);
					t0 = t.place();
				}
			}
			block.pos(p[0], p[1]).place();
		}
	}

}
