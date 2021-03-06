package cd4017be.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.util.*;
import java.util.function.Function;

/**A {@link DataOutputStream} with additional utility functions.
 * @author CD4017BE */
public class ExtOutputStream extends DataOutputStream {

	public ExtOutputStream(OutputStream out) {
		super(out);
	}

	/**@return the String to write. It must not be larger than 255 bytes after UTF-8 encoding!
	 * @throws IOException on I/O error */
	public void writeSmallUTF(String s) throws IOException {
		if (s == null) {
			write(0);
			return;
		}
		byte[] data = s.getBytes(UTF_8);
		if (data.length > 255) throw new IllegalArgumentException("String too long");
		out.write(data.length);
		out.write(data);
	}

	/**@param v a non-negative integer to write using a variable number of bytes depending on size
	 * @throws IOException on I/O error */
	public void writeVarInt(int v) throws IOException {
		if (v < 0) throw new IllegalArgumentException("number must not be negative");
		do {
			out.write(v < 128 ? v : v | 128);
		} while ((v >>>= 7) != 0);
	}

	/**@param v a non-negative integer <= max to write using the number of bytes required to represent max
	 * @param max maximum possible value for v
	 * @throws IOException on I/O error */
	public void writeInt(int v, int max) throws IOException {
		if (v < 0 || v > max) throw new IllegalArgumentException("number out of range");
		if (max == 0) return;
		out.write(v);
		if (max <= 0xff) return;
		out.write(v >>> 8);
		if (max <= 0xffff) return;
		out.write(v >>> 16);
		if (max > 0xffffff) return;
		out.write(v >>> 24);
	}

	/**@param <T> element type
	 * @param objects to write using encoder
	 * @param encoder
	 * @throws IOException on I/O error */
	public <T> void writeArray(Collection<T> objects, Encoder<T> encoder) throws IOException {
		writeVarInt(objects.size());
		for (T obj : objects) encoder.write(this, obj);
	}

	public class IDTable<T, I> {
		private final HashMap<I, Integer> indices = new HashMap<>();
		private final Function<T, I> getId;

		public IDTable(Collection<T> objects, Function<T, I> getId, Encoder<I> encoder)
		throws IOException {
			this.getId = getId;
			ArrayList<I> ids = new ArrayList<>();
			for (T obj : objects) {
				I key = getId.apply(obj);
				if (indices.putIfAbsent(key, ids.size()) == null)
					ids.add(key);
			}
			writeArray(ids, encoder);
		}

		public void writeId(T obj) throws IOException {
			writeInt(indices.get(getId.apply(obj)), indices.size() - 1);
		}
	}

	@FunctionalInterface
	public interface Encoder<T> {
		void write(ExtOutputStream out, T obj) throws IOException;
	}

}
