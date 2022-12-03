package cd4017be.compiler;

import static java.lang.Integer.numberOfLeadingZeros;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static org.lwjgl.opengl.GL12C.GL_BGRA;
import static org.lwjgl.opengl.GL12C.GL_UNSIGNED_SHORT_1_5_5_5_REV;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.editor.*;
import cd4017be.util.*;

/**
 * 
 * @author CD4017BE */
public class CircuitFile {
	private static final int
	GRAPH_MAGIC = 'd' | 'f' << 8 | 'c' << 16 | 'G' << 24,
	MODEL_MAGIC = 'd' | 'f' << 8 | 'c' << 16 | 'M' << 24,
	CIRCUIT_VERSION = 0, LAYOUT_VERSION = 0, MODEL_VERSION = 0;

	private static void checkMagic(ExtInputStream eis, int magic) throws IOException {
		if (eis.readI32() != magic) throw new IOException("wrong file format");
	}

	public static void readModel(BlockModel model, IconAtlas icons) throws IOException {
		try (ExtInputStream is = new ExtInputStream(newInputStream(model.module.path.resolve("models/" + model.name + ".dfcm")))) {
			checkMagic(is, MODEL_MAGIC);
			is.readU8(MODEL_VERSION);
			is.readAll(model.outs = new byte[is.readU8() * 2]);
			is.readAll(model.ins = new byte[is.readU8() * 2]);
			model.tx = is.readI8();
			model.ty = is.readI8();
			model.tw = is.readI8();
			model.th = is.readI8();
			try(MemoryStack ms = MemoryStack.stackPush()) {
				icons.load(GLUtils.readImage(is, ms), model);
			}
		}
	}

	public static void writeModel(BlockModel model, IconAtlas icons) throws IOException {
		try (ExtOutputStream os = new ExtOutputStream(Files.newOutputStream(model.module.path.resolve("models/" + model.name + ".dfcm")))) {
			os.write16(MODEL_MAGIC);
			os.write8(MODEL_VERSION);
			os.write8(model.outs.length);
			os.write(model.outs);
			os.write8(model.ins.length);
			os.write(model.ins);
			os.write8(model.tx);
			os.write8(model.ty);
			os.write8(model.tw);
			os.write8(model.th);
			writeIcon(os, icons, model.icon);
		}
	}

	private static void writeIcon(ExtOutputStream os, IconAtlas icons, AtlasSprite icon) throws IOException {
		try (MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer pixels = icons.getData(ms, 0, GL_BGRA, GL_UNSIGNED_SHORT_1_5_5_5_REV, 2);
			int scan = pixels.getShort();
			pixels.position(2 + icon.x * 8 + icon.y * 4 * scan);
			int w = icon.w * 4, h = icon.h * 4;
			try {
				int l = w * h, palLen = Math.min(512, l * 2);
				//create color palette
				ByteBuffer palette = ByteBuffer.allocate(palLen).order(LITTLE_ENDIAN);
				byte[] indices = new byte[l];
				for (int y = 0, i = 0, p = pixels.mark().position(); y < h; y++, p += scan) {
					pixels.position(p);
					for (int x = 0, j; x < w; x++, i++) {
						short c = pixels.getShort();
						for (j = 0; j < palette.position(); j+=2)
							if (palette.getShort(j) == c) break;
						if (j == palette.position())
							palette.putShort(c); //may throw BufferOverflowException
						indices[i] = (byte)(j >> 1);
					}
				}
				//write header
				os.write8(0b10_0111_01);
				os.write8(w - 1);
				os.write8(h - 1);
				//write palette
				int p = (palette.position() >> 1) - 1;
				os.write8(p);
				os.write(palette.array(), 0, palette.position());
				//write indices
				int bits = 32 - numberOfLeadingZeros(p), d = 0, b = 0;
				for (int i = 0; i < l; i++) {
					d |= (indices[i] & 0xff) << b;
					if ((b += bits) > 8) {
						os.write8(d);
						d >>>= 8; b -= 8;
					}
				}
				os.write8(d);
			} catch(BufferOverflowException e) {
				//palette too big -> save raw
				os.write8(0b00_0111_01);
				os.write8(w - 1);
				os.write8(h - 1);
				byte[] buf = new byte[w * 2];
				for (int y = 0, p = pixels.reset().position(); y < h; y++, p += scan) {
					pixels.get(p, buf);
					os.write(buf);
				}
			}
		}
	}

	public static Path path(BlockDef def) {
		return def.module.path.resolve("blocks/" + def.id + ".dfc");
	}

	public static ExtInputStream readBlock(BlockDef def) throws IOException {
		return new ExtInputStream(newInputStream(path(def)));
	}

	public static void readLayout(ExtInputStream is, Module m, CircuitEditor cc) throws IOException {
		BlockInfo[] circuit = readCircuit(is, m);
		is.readU8(LAYOUT_VERSION);
		Trace[] traces = new Trace[is.readVarInt() + 1];
		Block[] blocks = new Block[circuit.length];
		int no = 1, ni = 1;
		for (BlockInfo info : circuit)
			ni += info.outs;
		for (int i = 0; i < blocks.length; i++) {
			Block block = blocks[i] = new Block(circuit[i]);
			block.pos(is.readI16(), is.readI16(), cc);
			int j = 0;
			for (Trace tr : block.io)
				traces[j++ < block.outs ? no++ : ni++] = tr;
		}
		for (int i = ni; i < traces.length; i++)
			traces[i] = new Trace();
		for (int i = 1; i < traces.length; i++) {
			Trace tr = traces[i];
			tr.pos(is.readI16(), is.readI16(), cc);
			if (i >= no) tr.connect(traces[is.readInt(traces.length - 1)], cc);
			tr.add(cc);
		}
		for (Block block : blocks) block.add(cc);
	}

	public static BlockInfo[] readCircuit(ExtInputStream is, Module m) throws IOException {
		checkMagic(is, GRAPH_MAGIC);
		is.readU8(CIRCUIT_VERSION);
		Module[] modules = new Module[is.readVarInt() + 1];
		modules[0] = m;
		for (int i = 1; i < modules.length; i++) {
			String name = is.readSmallUTF8();
			Module mod = modules[i] = m.imports.get(name);
			if (mod != null) mod.ensureLoaded();
			else System.out.printf("missing module '%s'\n", name);
		}
		BlockInfo[] defs = new BlockInfo[is.readVarInt()];
		for (int i = 0; i < defs.length; i++) {
			Module mod = modules[is.readInt(modules.length - 1)];
			int out = is.readU8(), in = is.readU8(), arg = is.readU8();
			String name = is.readSmallUTF8();
			BlockDef def = null;
			if (mod != null && (def = mod.blocks.get(name)) == null)
				System.out.printf("missing block '%s' in module '%s'\n", name, mod);
			defs[i] = new BlockInfo(def != null ? def : m.cache.placeholder, out, in, arg);
		}
		String[] args = new String[is.readVarInt()];
		for (int i = 0; i < args.length; i++)
			args[i] = is.readUTF8();
		BlockInfo[] blocks = new BlockInfo[is.readVarInt()];
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo def = defs[is.readInt(defs.length - 1)];
			int[] in = new int[def.ins.length];
			for (int j = 0; j < in.length; j++)
				in[j] = is.readInt(blocks.length) - 1 | is.readU8() << 16;
			String[] arg = new String[def.args.length];
			for (int j = 0; j < arg.length; j++)
				arg[j] = args[is.readInt(args.length - 1)];
			blocks[i] = new BlockInfo(def.def, def.outs, in, arg);
		}
		return blocks;
	}

	private static <T> int index(LinkedHashMap<T, Integer> map, T elem) {
		int l = map.size();
		Integer i = map.putIfAbsent(elem, l);
		return i != null ? i : l;
	}

	public static void writeLayout(BlockDef def, IndexedSet<Block> blocks, IndexedSet<Trace> traces)
	throws IOException {
		Path path = path(def);
		Files.createDirectories(path.getParent());
		try(ExtOutputStream os = new ExtOutputStream(newOutputStream(path))) {
			BlockInfo[] circuit = new BlockInfo[blocks.size()];
			int no = 0, ni = 0;
			for (int i = 0; i < circuit.length; i++) {
				Block block = blocks.get(i);
				int o = block.outs;
				no += o;
				int[] in = new int[block.ins()];
				for (int j = 0; j < in.length; j++, ni++) {
					Trace tr = block.io[j + o];
					traces.add(ni, tr);
					int id;
					for (id = -1; tr != null; tr = tr.from)
						if (tr.isOut()) {
							id = tr.block.getIdx() | tr.pin << 16;
							break;
						}
					in[j] = id;
				}
				circuit[i] = new BlockInfo(block.def, o, in, block.args);
			}
			ni = traces.size() + no;
			writeCircuit(os, def.module, circuit);
			os.write8(LAYOUT_VERSION);
			os.writeVarInt(ni);
			Trace[] outs = new Trace[no];
			no = 0;
			for (Block block : blocks) {
				os.write16(block.x);
				os.write16(block.y);
				for (int i = 0; i < block.outs; i++, no++)
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

	public static void writeCircuit(ExtOutputStream os, Module m, BlockInfo[] blocks) throws IOException {
		LinkedHashMap<BlockInfo, Integer> defs = new LinkedHashMap<>();
		LinkedHashMap<String, Integer> args = new LinkedHashMap<>();
		LinkedHashMap<Module, Integer> modules = new LinkedHashMap<>();
		int[][] blockIds = new int[blocks.length][];
		for (int i = 0; i < blocks.length; i++) {
			BlockInfo block = blocks[i];
			int[] ids = new int[block.args.length + 1];
			ids[0] = index(defs, block);
			int j = 1;
			for (String arg : block.args)
				ids[j++] = index(args, arg);
			blockIds[i] = ids;
		}
		int[] defIds = new int[defs.size()];
		int i = 0;
		for (BlockInfo def : defs.keySet()) {
			Module mod = def.def.module;
			defIds[i++] = mod == null || mod == m ? 0 : index(modules, mod) + 1;
		}
		os.write32(GRAPH_MAGIC);
		os.write8(CIRCUIT_VERSION);
		os.writeVarInt(modules.size());
		for (Module mod : modules.keySet())
			os.writeSmallUTF(m.name(mod));
		os.writeVarInt(defIds.length);
		i = 0;
		for (BlockInfo def : defs.keySet()) {
			os.writeInt(defIds[i++], modules.size());
			os.write8(def.outs);
			os.write8(def.ins.length);
			os.write8(def.args.length);
			os.writeSmallUTF(def.def.id);
		}
		os.writeVarInt(args.size());
		for (String s : args.keySet())
			os.writeUTF(s);
		os.writeVarInt(blocks.length);
		for (i = 0; i < blocks.length; i++) {
			int[] ids = blockIds[i];
			os.writeInt(ids[0], defs.size() - 1);
			for (int in : blocks[i].ins) {
				os.writeInt(in + 1, blocks.length);
				os.write8(in >> 16);
			}
			for (int j = 1; j < ids.length; j++)
				os.writeInt(ids[j], args.size() - 1);
		}
	}

}
