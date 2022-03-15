package cd4017be.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.util.function.IntFunction;
import java.util.zip.DataFormatException;

/**A {@link DataInputStream} with additional utility functions.
 * @author CD4017BE */
public class ExtInputStream extends DataInputStream {

	public ExtInputStream(InputStream in) {
		super(in);
	}

	private final byte[] buf = new byte[255];

	/**@return an up to 255 byte sized UTF-8 encoded String read from this stream
	 * @throws IOException on I/O error or EOF */
	public String readSmallUTF() throws IOException {
		int l = readUnsignedByte();
		readFully(buf, 0, l);
		return new String(buf, 0, l, UTF_8);
	}

	/**@return a non-negative integer constructed using a variable number of bytes depending on size
	 * @throws IOException on I/O error or EOF */
	public int readVarInt() throws IOException {
		int v;
		if ((v = (byte)in.read()) >= 0
			|| (v |= (byte)in.read() << 7) >= 0
			|| (v |= (byte)in.read() << 14) >= 0
			|| (v |= (byte)in.read() << 21) >= 0
			|| (v |= in.read() << 28) >= 0
		) return v;
		throw new EOFException();
	}

	/**@param max the largest allowed value
	 * @return a non-negative integer <= max, constructed using the number of bytes required to represent max.
	 * @throws IOException on I/O error or EOF
	 * @throws DataFormatException if the data would encode a number > max */
	public int readInt(int max) throws IOException, DataFormatException {
		int v =
		max <= 0 ? 0 : in.read() | (
			max <= 0xff ? 0 : in.read() << 8 | (
				max <= 0xffff ? 0 : in.read() << 16 | (
					max <= 0xffffff ? 0 : in.read() << 24
				)
			)
		);
		if (v < 0) throw new EOFException();
		if (v > max) throw new DataFormatException();
		return v;
	}

	/**@param <T> array element type
	 * @param arrayInit an Array constructor
	 * @param decoder a Decoder that constructs objects of type T from stream data
	 * @return a variable length array read from this stream
	 * @throws IOException on I/O error or EOF
	 * @throws DataFormatException thrown from decoder */
	public <T> T[] readArray(IntFunction<T[]> arrayInit, Decoder<T> decoder)
	throws IOException, DataFormatException {
		T[] arr = arrayInit.apply(readVarInt());
		for (int i = 0; i < arr.length; i++)
			arr[i] = decoder.read(this);
		return arr;
	}

	/**@param <T> array element type
	 * @param lookuptable the array to index
	 * @return an element from lookuptable whose index is read from this stream via {@link #readInt(int)}
	 * @throws IOException on I/O error or EOF
	 * @throws DataFormatException if the data would encode an index that is out of range */
	public <T> T readElement(T[] lookuptable) throws IOException, DataFormatException {
		return lookuptable[readInt(lookuptable.length - 1)];
	}

	@FunctionalInterface
	public interface Decoder<T> {
		T read(ExtInputStream in) throws IOException, DataFormatException;
	}

}
