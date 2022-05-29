package cd4017be.dfc.lang;

import static java.lang.Integer.numberOfLeadingZeros;
import static java.lang.Math.min;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

import org.lwjgl.system.MemoryStack;

import cd4017be.dfc.editor.*;
import cd4017be.dfc.graph.BlockInfo;
import cd4017be.util.IndexedSet;

/**
 * @author CD4017BE */
public class CircuitFile implements Closeable {

	public static final Predicate<String> FILTER = name -> name.endsWith(".dfc");
	private static final OpenOption[] LOAD = {READ}, EDIT = {READ, WRITE, CREATE};
	private static final int MAGIC = 'd' | 'f' << 8 | 'c' << 16 | 0 << 24;
	private static final int HEADER_SIZE = 44;
	public static final int
	HEADER = 0, INTERFACE = 1, CIRCUIT = 2,
	LAYOUT = 3, ICON = 4, DESCRIPTION = 5;

	private final SeekableByteChannel ch;
	private final int[] hdr;

	public CircuitFile(Path path, boolean write) throws IOException {
		this.ch = Files.newByteChannel(path, write ? EDIT : LOAD);
		this.hdr = new int[12];
		hdr[1] = HEADER_SIZE;
		try {
			if (ch.size() >= HEADER_SIZE) {
				ByteBuffer buf = read(HEADER);
				if (buf.getInt() == MAGIC) {
					for (int i = 2; i < hdr.length; i++)
						hdr[i] = buf.getInt();
					return;
				}
			}
			if (!write) throw new IOException("missing header");
		} catch (IOException e) {
			ch.close();
			throw e;
		}
	}

	public int length(int section) {
		return hdr[section<<1|1];
	}

	private ByteBuffer read(int section) throws IOException {
		int l = length(section);
		if (l <= 0) return null;
		ch.position(hdr[section<<1]);
		ByteBuffer buf = alloc(l);
		while(buf.hasRemaining())
			if (ch.read(buf) < 0)
				throw new EOFException();
		return buf.flip();
	}

	/**Read the interface section.
	 * @param def the block definition to initialize
	 * @throws IOException */
	public BlockDef readInterface(String name) throws IOException {
		ByteBuffer buf = read(INTERFACE);
		if (buf == null) throw new IOException("missing interface section");
		try {
			int out = buf.get() & 255, in = buf.get() & 255, arg = buf.get() & 1;
			BlockDef def = new BlockDef(name, out, in, arg != 0);
			buf.get(def.pins);
			for (int i = 0; i < def.ioNames.length; i++)
				def.ioNames[i] = getString(buf);
			return def;
		} catch (BufferUnderflowException e) {
			throw new IOException(e);
		}
	}

	/**Read the circuit section.
	 * @param reg the block registry
	 * @return the circuit implementation graph
	 * @throws IOException */
	public BlockInfo[] readCircuit(BlockRegistry reg) throws IOException {
		ByteBuffer buf = read(CIRCUIT);
		if (buf == null) return null;
		BlockDef[] defs = new BlockDef[buf.getChar()];
		for (int i = 0; i < defs.length; i++) {
			int out = buf.get() & 255, in = buf.get() & 255, arg = buf.get() & 1;
			defs[i] = reg.get(getString(buf), out, in, arg != 0);
		}
		String[] args = new String[buf.getChar()];
		for (int i = 0; i < args.length; i++)
			args[i] = getString(buf);
		BlockInfo[] blocks = new BlockInfo[buf.getChar()];
		for (int i = 0; i < blocks.length; i++) {
			BlockDef def = defs[buf.getChar()];
			int[] pins = new int[def.inCount];
			for (int j = 0; j < pins.length; j++)
				pins[j] = buf.getChar() - 1;
			String[] arg;
			if (def.hasArg) {
				arg = new String[buf.get() & 0xff];
				for (int j = 0; j < arg.length; j++)
					arg[j] = args[buf.getChar()];
			} else arg = new String[0];
			blocks[i] = new BlockInfo(def, arg, pins);
		}
		return blocks;
	}

	/**Read the layout section.
	 * @param cc the circuit editor to initialize
	 * @param circuit the circuit implementation graph
	 * @throws IOException */
	public void readLayout(CircuitEditor cc, BlockInfo[] circuit) throws IOException {
		ByteBuffer buf = read(LAYOUT);
		if (buf == null) return;
		for (BlockInfo info : circuit) {
			Block block = new Block(info.def(), cc);
			block.setText(String.join(", ", info.arguments()));
			block.pos(buf.getShort(), buf.getShort());
			for (int i = 0, l = block.def.ios(); i < l; i++) {
				int n = i < block.def.outCount ? 0 : buf.get() & 255;
				Trace t0 = block.io[i].pos(buf.getShort(), buf.getShort());
				while(--n >= 0) {
					Trace t = new Trace(cc).pos(buf.getShort(), buf.getShort());
					t0.connect(t);
					t0 = t.place();
				}
			}
			block.place();
		}
	}

	public InputStream readIcon() throws IOException {
		ByteBuffer buf = read(ICON);
		return buf == null ? null
			: new ByteArrayInputStream(buf.array(), buf.position(), buf.limit());
	}

	/** Read the description section.
	 * @param full whether to read the full string
	 * @return the description string
	 * @throws IOException */
	public void readDescription(BlockDef def) throws IOException {
		ByteBuffer buf = read(DESCRIPTION);
		if (buf != null) {
			def.shortDesc = getString(buf);
			def.longDesc = getString(buf);
		} else def.shortDesc = def.longDesc = "";
	}

	private void write(int section, ByteBuffer buf) throws IOException {
		clear(section);
		int l = buf.remaining(), p = 0;
		if (section != HEADER) {
			//find first free block in file that is sufficiently large.
			int[] sections = new int[hdr.length + 1];
			for (int i = 0; i < hdr.length; i+=2)
				sections[i+1] = (sections[i] = hdr[i]) + hdr[i+1];
			sections[hdr.length] = Integer.MAX_VALUE;
			Arrays.sort(sections);
			for (int i = 1; i < sections.length - 1; i+=2)
				if(sections[i + 1] - sections[i] >= l) {
					p = sections[i];
					break;
				}
		}
		hdr[section<<1|1] = l;
		ch.position(hdr[section<<1] = p);
		while(buf.hasRemaining()) ch.write(buf);
	}

	public void clear(int section) {
		hdr[section<<1] = hdr[section<<1|1] = 0;
	}

	public void writeHeader() throws IOException {
		ByteBuffer buf = alloc(HEADER_SIZE).putInt(MAGIC);
		for (int i = 2; i < hdr.length; i++) buf.putInt(hdr[i]);
		write(HEADER, buf.flip());
		int max = 0;
		for (int i = 0; i < hdr.length; i += 2)
			max = Math.max(max, hdr[i] + hdr[i+1]);
		ch.truncate(max);
	}

	public void writeInterface(BlockDef def) throws IOException {
		byte[][] ioNames = new byte[def.ioNames.length][];
		int l = 3 + def.pins.length;
		for (int i = 0; i < ioNames.length; i++)
			l += (ioNames[i] = def.ioNames[i].getBytes(UTF_8)).length + 1;
		ByteBuffer buf = alloc(l)
		.put((byte)def.outCount)
		.put((byte)def.inCount)
		.put((byte)(def.hasArg ? 1 : 0))
		.put(def.pins);
		for (byte[] arr : ioNames)
			buf.put(arr).put((byte)0);
		write(INTERFACE, buf.flip());
	}

	public void writeCircuit(BlockInfo[] blocks) throws IOException {
		LinkedHashMap<BlockDef, Integer> defs = new LinkedHashMap<>();
		LinkedHashMap<String, Integer> args = new LinkedHashMap<>();
		int l = 6;
		for (BlockInfo block : blocks) {
			BlockDef def = block.def();
			defs.putIfAbsent(def, defs.size());
			l += (def.inCount + 1) * 2;
			if (def.hasArg) {
				l += block.arguments().length * 2 + 1;
				for (String s : block.arguments())
					args.putIfAbsent(s, args.size());
			}
		}
		byte[][] strings = new byte[defs.size() + args.size()][];
		int i = 0;
		for (BlockDef def : defs.keySet())
			l += (strings[i++] = def.name.getBytes(UTF_8)).length + 4;
		for (String arg : args.keySet())
			l += (strings[i++] = arg.getBytes(UTF_8)).length + 1;
		ByteBuffer buf = alloc(l);
		i = 0;
		buf.putShort((short)defs.size());
		for (BlockDef def : defs.keySet())
			buf.put((byte)def.outCount)
			.put((byte)def.inCount)
			.put((byte)(def.hasArg ? 1 : 0))
			.put(strings[i++]).put((byte)0);
		buf.putShort((short)args.size());
		while(i < strings.length)
			buf.put(strings[i++]).put((byte)0);
		buf.putShort((short)blocks.length);
		for (BlockInfo block : blocks) {
			BlockDef def = block.def();
			int[] in = block.inputs();
			buf.putShort(defs.get(def).shortValue());
			for (int j = 0; j < def.inCount; j++)
				buf.putShort((short)(in[j] + 1));
			if (def.hasArg) {
				String[] ss = block.arguments();
				buf.put((byte)ss.length);
				for (String s : ss)
					buf.putShort(args.get(s).shortValue());
			}
		}
		write(CIRCUIT, buf.flip());
	}

	public static int parseArgument(String s, int[] argbuf) {
		int n = 0, br = 0;
		char str = 0;
		for (int j = 0; j < s.length(); j++) {
			char c = s.charAt(j);
			if (str != 0) {
				if (c == str) str = 0;
				else if (c == '\\') j++;
				continue;
			}
			switch(c) {
			case ',' -> {
				if (br <= 0 && n < argbuf.length)
					argbuf[n++] = j;
			}
			case '"', '\'' -> str = c;
			case '(', '[', '{' -> br++;
			case ')', ']', '}' -> br--;
			}
		}
		return n;
	}

	public BlockInfo[] writeLayout(IndexedSet<Block> layout) throws IOException {
		BlockInfo[] blocks = new BlockInfo[layout.size()];
		int l = 0;
		int[] argbuf = new int[254];
		for (int i = 0; i < blocks.length; i++) {
			Block block = layout.get(i);
			int o = block.def.outCount;
			l += (o + 1) * 4;
			int[] in = new int[block.def.inCount];
			inputs: for (int j = 0; j < in.length; j++) {
				l++;
				int tracing = 256;
				for (Trace tr = block.io[j + o], t1; tr != null; tr = t1) {
					t1 = tr.from;
					if (tracing > 0) {
						l += 4;
						tracing = t1 != null && t1.to == tr ? tracing - 1 : 0;
					}
					if (tr.isOut()) {
						in[j] = tr.block.getIdx();
						continue inputs;
					}
				}
				in[j] = -1;
			}
			String s, arg[];
			if (block.def.hasArg) {
				int n = parseArgument(s = block.text(), argbuf);
				arg = new String[n + 1];
				int p = 0;
				for (int j = 0; j < n; j++, p++)
					arg[j] = s.substring(p, p = argbuf[j]).trim();
				arg[n] = s.substring(p).trim();
			} else arg = null;
			blocks[i] = new BlockInfo(block.def, arg, in);
		}
		clear(LAYOUT);
		writeCircuit(blocks);
		ByteBuffer buf = alloc(l);
		for (int i = 0; i < blocks.length; i++) {
			Block block = layout.get(i);
			buf.putShort(block.x).putShort(block.y);
			for (int j = 0; j < block.io.length; j++) {
				Trace tr = block.io[j], t0;
				if (j < block.def.outCount) {
					buf.putShort(tr.x()).putShort(tr.y());
					continue;
				}
				buf.mark().put((byte)0);
				int n = 0;
				do {
					if (++n > 255) buf.position(buf.position() - 4);
					buf.putShort(tr.x()).putShort(tr.y());
					tr = (t0 = tr).from;
				} while(tr != null && tr.to == t0 && !t0.isOut());
				int p = buf.position();
				buf.reset().put((byte)min(n - 1, 255)).position(p);
			}
		}
		write(LAYOUT, buf.flip());
		return blocks;
	}

	public void writeIcon(ByteBuffer pixels, int scan, int w, int h) throws IOException {
		try (MemoryStack ms = MemoryStack.stackPush()) {
			int l = w * h, palLen = Math.min(512, l * 2);
			ByteBuffer data = ms.malloc(4 + palLen + l);
			//write header
			data.put((byte)0b10_0111_01);
			data.put((byte)(w - 1));
			data.put((byte)(h - 1));
			try {
				//create color palette
				ByteBuffer palette = data.slice(4, palLen).order(LITTLE_ENDIAN);
				ByteBuffer indices = data.slice(4 + palLen, l).order(LITTLE_ENDIAN);
				for (int y = 0, p = pixels.mark().position(); y < h; y++, p += scan) {
					pixels.position(p);
					for (int x = 0, j; x < w; x++) {
						short c = pixels.getShort();
						for (j = 0; j < palette.position(); j+=2)
							if (palette.getShort(j) == c) break;
						if (j == palette.position())
							palette.putShort(c); //may throw BufferOverflowException
						indices.put((byte)(j >> 1));
					}
				}
				//write palette
				int p = (palette.position() >> 1) - 1;
				data.put((byte)p);
				data.position(data.position() + palette.position());
				//write indices
				int bits = 32 - numberOfLeadingZeros(p), d = 0, b = 0;
				for (indices.flip(); indices.hasRemaining();) {
					d |= (indices.get() & 0xff) << b;
					if ((b += bits) > 8) {
						data.put((byte)d);
						d >>>= 8; b -= 8;
					}
				}
				data.put((byte)d);
			} catch(BufferOverflowException e) {
				//palette too big -> save raw
				data.put(0, (byte)0b00_0111_01);
				pixels.reset().limit(pixels.position() + w * 2);
				for (int y = 0, p = pixels.reset().position(); y < h; y++, p += scan)
					data.put(pixels.limit(p + w * 2).position(p));
			}
			write(ICON, data.flip());
		}
	}

	public void writeIcon(ByteArrayOutputStream out) throws IOException {
		write(ICON, ByteBuffer.wrap(out.toByteArray()));
	}

	public void writeDescription(BlockDef def) throws IOException {
		byte[] sd = def.shortDesc.getBytes(UTF_8);
		byte[] ld = def.longDesc.getBytes(UTF_8);
		ByteBuffer buf = alloc(sd.length + ld.length + 2);
		buf.put(sd).put((byte)0).put(ld).put((byte)0);
		write(DESCRIPTION, buf.flip());
	}

	@Override
	public void close() throws IOException {
		ch.close();
	}

	private static ByteBuffer alloc(int size) {
		return ByteBuffer.allocate(size).order(LITTLE_ENDIAN);
	}

	private static String getString(ByteBuffer buf) {
		int p = buf.position();
		while (buf.get() != 0);
		return new String(buf.array(), p, buf.position() - 1 - p, UTF_8);
	}

}
