package cd4017be.dfc.lang;

import static java.nio.file.Files.*;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import cd4017be.dfc.editor.circuit.Block;
import cd4017be.dfc.editor.circuit.Trace;
import cd4017be.util.*;

/**
 * 
 * @author CD4017BE */
public class CircuitFile {
	private static final int
	GRAPH_MAGIC = 'd' | 'f' << 8 | 'c' << 16 | 'G' << 24,
	SIGNAL_MAGIC= 'd' | 'f' << 8 | 'c' << 16 | 'S' << 24,
	CIRCUIT_VERSION = 2, LAYOUT_VERSION = 0, SIGNAL_VERSION = 0;

	private static void checkMagic(ExtInputStream eis, int magic) throws IOException {
		if (eis.readI32() != magic) throw new IOException("wrong file format");
	}

	/**Completely load a resource into a byte array.
	 * @param url the resource to load
	 * @param maxLen resource size limit in bytes
	 * (to protect against excessive memory allocation)
	 * @return the resource data
	 * @throws IOException on I/O error or in case of invalid size */
	public static byte[] loadResource(URL url, int maxLen) throws IOException {
		URLConnection con = url.openConnection();
		int len = con.getContentLength();
		if (len < 0 || len > maxLen)
			throw new IOException("invalid resource size: " + len);
		try (InputStream is = con.getInputStream()) {
			byte[] data = new byte[len];
			for (int i = 0, n; len > 0; i += n, len -= n)
				if ((n = is.read(data, i, len)) < 0)
					throw new EOFException();
			return data;
		}
	}

	public static Path path(BlockDef def) {
		return def.module.path.resolve(
			def.id.isEmpty() ? "module.dfc" : "blocks/" + def.id + ".dfc"
		);
	}

	public static ExtInputStream readBlock(BlockDef def) throws IOException {
		assert(Thread.holdsLock(def));
		return new ExtInputStream(newInputStream(path(def)));
	}

	public static record Layout(Block[] blocks, Trace[] traces) {}

	public static Layout readLayout(ExtInputStream is, Module m) throws IOException {
		IndexedSet<BlockDesc> circuit = readCircuit(is, m);
		is.readU8(LAYOUT_VERSION);
		Trace[] traces = new Trace[is.readVarInt() + 1];
		Block[] blocks = new Block[circuit.size()];
		int no = 1, ni = 1;
		for (BlockDesc info : circuit)
			ni += info.outs.length;
		for (int i = 0; i < blocks.length; i++) {
			Block block = blocks[i] = new Block(circuit.get(i));
			block.pos(is.readI16(), is.readI16());
			int j = 0;
			for (Trace tr : block.io)
				traces[j++ < block.outs() ? no++ : ni++] = tr;
		}
		for (int i = ni; i < traces.length; i++)
			traces[i] = new Trace();
		for (int i = 1; i < traces.length; i++) {
			Trace tr = traces[i];
			tr.pos(is.readI16(), is.readI16());
			if (i >= no) tr.connect(traces[is.readInt(traces.length - 1)]);
		}
		return new Layout(blocks, traces);
	}

	public static IndexedSet<BlockDesc> readCircuit(ExtInputStream is, Module m) throws IOException {
		checkMagic(is, GRAPH_MAGIC);
		boolean mref = is.readU8(CIRCUIT_VERSION) < CIRCUIT_VERSION;
		Module[] modules = new Module[is.readVarInt() + (mref ? 1 : 0)];
		if (mref) modules[0] = m;
		for (int i = mref ? 1 : 0; i < modules.length; i++) {
			String name = mref ? is.readL8UTF8() : is.readUTF8();
			Module mod = mref ? m.imports.get(name) : LoadingCache.getModule(name);
			if (mod == null) {
				System.out.printf("missing module '%s'\n", name);
				mod = LoadingCache.LOADER;
			}
			modules[i] = mod;
		}
		BlockDesc[] defs = new BlockDesc[is.readVarInt()];
		for (int i = 0; i < defs.length; i++) {
			Module mod = modules[is.readInt(modules.length - 1)];
			int out = is.readU8(), in = is.readU8(), arg = is.readU8();
			String name = mref ? is.readL8UTF8() : is.readUTF8();
			defs[i] = new BlockDesc(mod.getBlock(name), out, in, arg);
		}
		String[] args = new String[is.readVarInt()];
		for (int i = 0; i < args.length; i++)
			args[i] = mref ? is.readL16UTF8() : is.readUTF8();
		int n = is.readVarInt();
		IndexedSet<BlockDesc> blocks = new IndexedSet<>(new BlockDesc[n]);
		for (int i = 0; i < n; i++) {
			BlockDesc def = defs[is.readInt(defs.length - 1)];
			int[] in = new int[def.ins.length];
			for (int j = 0; j < in.length; j++)
				in[j] = is.readInt(n) - 1 << 8 | is.readU8();
			String[] arg = new String[def.args.length];
			for (int j = 0; j < arg.length; j++)
				arg[j] = args[is.readInt(args.length - 1)];
			blocks.add(new BlockDesc(def.def, def.outs.length, in, arg));
		}
		for (BlockDesc block : blocks)
			for (int i = 0; i < block.ins(); i++) {
				int k = block.inLinks[i];
				block.inLinks[i] = k & 0xff;
				block.inBlocks[i] = k < 0 ? null : blocks.get(k >> 8);
			}
		return blocks;
	}

	private static <T> int index(LinkedHashMap<T, Integer> map, T elem) {
		int l = map.size();
		Integer i = map.putIfAbsent(elem, l);
		return i != null ? i : l;
	}

	public static void writeLayout(BlockDef def, List<Block> blocks, IndexedSet<Trace> traces)
	throws IOException {
		assert(Thread.holdsLock(def));
		Path path = path(def);
		createDirectories(path.getParent());
		try(ExtOutputStream os = new ExtOutputStream(newOutputStream(path))) {
			BitSet visited = new BitSet(traces.size());
			int no = 0, ni = 0;
			for (int i = 0; i < blocks.size(); i++) {
				Block block = blocks.get(i);
				int o = block.outs();
				no += o;
				ins: for (int j = 0; j < block.ins(); j++, ni++) {
					Trace tr = block.io[j + o];
					visited.clear();
					traces.add(ni, tr);
					for (; tr != null; tr = tr.from)
						if (tr.isOut()) {
							block.connectIn(j, tr.block, tr.pin);
							continue ins;
						} else if (visited.get(tr.getIdx()))
							break;
						else visited.set(tr.getIdx());
					block.connectIn(j, null, -1);
				}
			}
			ni = traces.size() + no;
			writeCircuit(os, def.module, blocks);
			os.write8(LAYOUT_VERSION);
			os.writeVarInt(ni);
			Trace[] outs = new Trace[no];
			no = 0;
			for (Block block : blocks) {
				os.write16(block.x);
				os.write16(block.y);
				for (int i = 0; i < block.outs(); i++, no++)
					(outs[no] = block.io[i]).setIdx(no - outs.length);
			}
			for (Trace tr : outs) {
				os.write16(tr.x());
				os.write16(tr.y());
			}
			no = outs.length + 1;
			for (Trace tr : traces) {
				os.write16(tr.x());
				os.write16(tr.y());
				tr = tr.from;
				os.writeInt(tr == null ? 0 : tr.getIdx() + no, ni);
			}
		}
	}

	public static void writeCircuit(ExtOutputStream os, Module m, List<? extends BlockDesc> blocks) throws IOException {
		LinkedHashMap<BlockDesc, Integer> defs = new LinkedHashMap<>();
		LinkedHashMap<String, Integer> args = new LinkedHashMap<>();
		LinkedHashMap<Module, Integer> modules = new LinkedHashMap<>();
		int l = blocks.size();
		int[][] blockIds = new int[l][];
		for (int i = 0; i < l; i++) {
			BlockDesc block = blocks.get(i);
			int[] ids = new int[block.args.length + 1];
			ids[0] = index(defs, block);
			int j = 1;
			for (String arg : block.args)
				ids[j++] = index(args, arg);
			blockIds[i] = ids;
		}
		int[] defIds = new int[defs.size()];
		int i = 0;
		for (BlockDesc def : defs.keySet()) {
			Module mod = def.def.module;
			defIds[i++] = index(modules, mod);
		}
		os.write32(GRAPH_MAGIC);
		os.write8(CIRCUIT_VERSION);
		os.writeVarInt(modules.size());
		for (Module mod : modules.keySet())
			os.writeUTF8(mod.name);
		os.writeVarInt(defIds.length);
		i = 0;
		for (BlockDesc def : defs.keySet()) {
			os.writeInt(defIds[i++], modules.size() - 1);
			os.write8(def.outs.length);
			os.write8(def.ins.length);
			os.write8(def.args.length);
			os.writeUTF8(def.def.id);
		}
		os.writeVarInt(args.size());
		for (String s : args.keySet())
			os.writeUTF8(s);
		os.writeVarInt(l);
		for (i = 0; i < l; i++) {
			int[] ids = blockIds[i];
			os.writeInt(ids[0], defs.size() - 1);
			BlockDesc block = blocks.get(i);
			for (int j = 0; j < block.ins(); j++) {
				BlockDesc in = block.inBlocks[j];
				if (in == null) {
					os.writeInt(0, l);
					os.write8(0);
				} else {
					os.writeInt(in.getIdx() + 1, l);
					os.write8(block.inLinks[j]);
				}
			}
			for (int j = 1; j < ids.length; j++)
				os.writeInt(ids[j], args.size() - 1);
		}
	}

	private static Path constPath(BlockDef def) {
		return def.module.path.resolve(
			def.id.isEmpty() ? "module.ds" : "out/" + def.id + ".ds"
		);
	}

	public static void writeSignals(BlockDef def, String[] keys, Value[] signals) throws IOException {
		if (signals.length != keys.length)
			throw new IllegalArgumentException("keys & signals must have same length");
		Path path = constPath(def);
		Files.createDirectories(path.getParent());
		//build all object indexes
		LinkedHashMap<Module, Integer> modules = new LinkedHashMap<>();
		LinkedHashMap<Type, Integer> types = new LinkedHashMap<>();
		LinkedHashMap<Long, Integer> values = new LinkedHashMap<>();
		LinkedHashMap<byte[], Integer> datas = new LinkedHashMap<>();
		LinkedHashMap<Value[], Integer> elementss = new LinkedHashMap<>();
		ArrayList<Value[]> stack = new ArrayList<>();
		elementss.put(signals, 0);
		stack.add(signals);
		while(!stack.isEmpty())
			for (Value val : stack.remove(stack.size() - 1)) {
				if (types.putIfAbsent(val.type, types.size()) == null)
					modules.putIfAbsent(val.type.module, modules.size());
				values.putIfAbsent(val.value, values.size());
				datas.putIfAbsent(val.data, datas.size());
				if (elementss.putIfAbsent(val.elements, elementss.size()) == null)
					stack.add(val.elements);
			}
		//write file
		try (ExtOutputStream os = new ExtOutputStream(newOutputStream(path))) {
			//write header
			os.write32(SIGNAL_MAGIC);
			os.write8(SIGNAL_VERSION);
			//write module descriptions
			os.writeVarInt(modules.size());
			for (Module module : modules.keySet())
				os.writeUTF8(module.name);
			//write type descriptions
			os.writeVarInt(types.size());
			for (Type type : types.keySet()) {
				os.writeInt(modules.get(type.module), modules.size() - 1);
				os.writeUTF8(type.id);
			}
			//write value descriptions
			os.writeVarInt(values.size());
			for (Long value : values.keySet())
				os.write64(value.longValue());
			//write data descriptions
			os.writeVarInt(datas.size());
			for (byte[] data : datas.keySet()) {
				os.writeVarInt(data.length);
				os.write(data);
			}
			//write elements descriptions
			os.writeVarInt(elementss.size());
			for (Value[] elements : elementss.keySet())
				os.writeVarInt(elements.length);
			for (Value[] elements : elementss.keySet())
				for (Value e : elements) {
					os.writeInt(types    .get(e.type    ), types    .size() - 1);
					os.writeInt(elementss.get(e.elements), elementss.size() - 1);
					os.writeInt(datas    .get(e.data    ), datas    .size() - 1);
					os.writeInt(values   .get(e.value   ), values   .size() - 1);
				}
			//write keys
			for (String k : keys) os.writeUTF8(k);
		}
	}

	public static void readSignals(BlockDef def, HashMap<String, Value> signals) throws IOException {
		Path path = constPath(def);
		try(ExtInputStream is = new ExtInputStream(newInputStream(path))) {
			//read header
			checkMagic(is, SIGNAL_MAGIC);
			if (is.readU8() != SIGNAL_VERSION)
				throw new IOException("unsupported format version");
			//read module descriptions
			Module[] modules = new Module[is.readVarInt()];
			for (int i = 0; i < modules.length; i++)
				modules[i] = LoadingCache.getModule(is.readUTF8());
			//read type descriptions
			Type[] types = new Type[is.readVarInt()];
			for (int i = 0; i < types.length; i++) {
				Module m = modules[is.readInt(modules.length - 1)];
				types[i] = m.getType(is.readUTF8());
			}
			//read value descriptions
			long[] values = new long[is.readVarInt()];
			for (int i = 0; i < values.length; i++)
				values[i] = is.readI64();
			//read data descriptions
			byte[][] datas = new byte[is.readVarInt()][];
			for (int i = 0; i < datas.length; i++) {
				int l = is.readVarInt();
				if (l == 0) datas[i] = Value.NO_DATA;
				else is.readAll(datas[i] = new byte[l]);
			}
			//read elements descriptions
			Value[][] elementss = new Value[is.readVarInt()][];
			if (elementss.length == 0) throw new IOException("missing signals");
			for (int i = 0; i < elementss.length; i++) {
				int l = is.readVarInt();
				elementss[i] = l == 0 ? Value.NO_ELEM : new Value[l];
			}
			for (Value[] elements : elementss)
				for (int i = 0; i < elements.length; i++) {
					elements[i] = new Value(
						types    [is.readInt(types    .length - 1)],
						elementss[is.readInt(elementss.length - 1)],
						datas    [is.readInt(datas    .length - 1)],
						values   [is.readInt(values   .length - 1)]
					);
				}
			//read keys
			for (Value val : elementss[0])
				signals.put(is.readUTF8(), val);
		}
	}

}
