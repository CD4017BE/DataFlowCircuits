package cd4017be.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.util.Objects;

/**An {@link InputStream} with additional utility functions.
 * @author CD4017BE */
public class ExtInputStream extends FilterInputStream {

	public ExtInputStream(InputStream in) {
		super(in);
	}

	/**Completely fill the given array with bytes from this stream.
	 * @param arr array to full
	 * @throws IOException on I/O error or EOF */
	public void readAll(byte[] arr) throws IOException {
		readAll(arr, 0, arr.length);
	}

	/**Completely read the given number of bytes from this stream into the given array.
	 * @param arr array to store in
	 * @param ofs array offset to start storing at
	 * @param len number of bytes to transfer
	 * @throws IOException on I/O error or EOF */
	public void readAll(byte[] arr, int ofs, int len) throws IOException {
		Objects.checkFromIndexSize(ofs, len, arr.length);
		for (int n = 0, count; n < len; n += count)
			if ((count = in.read(arr, ofs + n, len - n)) < 0)
				throw new EOFException();
	}

	/**@return the next unsigned 8-bit value read from this stream
	 * @throws IOException on I/O error or EOF */
	public int readU8() throws IOException {
		int b = in.read();
		if (b < 0) throw new EOFException();
		return b;
	}

	/**@param max maximum allowed value
	 * @return the next unsigned 8-bit value read from this stream which must be <= max
	 * @throws IOException on I/O error, EOF or value out of range */
	public int readU8(int max) throws IOException {
		return rangeCheck(readU8(), 0, max);
	}

	/**@return the next signed 8-bit value read from this stream
	 * @throws IOException on I/O error or EOF */
	public byte readI8() throws IOException {
		return (byte)readU8();
	}

	/**@param min minimum allowed value
	 * @param max maximum allowed value
	 * @return the next signed 8-bit value read from this stream which must be in the given range
	 * @throws IOException on I/O error, EOF or value out of range */
	public byte readI8(int min, int max) throws IOException {
		return (byte)rangeCheck((byte)readU8(), min, max);
	}

	/**@return the next unsigned 16-bit value read from this stream
	 * @throws IOException on I/O error or EOF */
	public int readU16() throws IOException {
		int b0 = in.read(), b1 = in.read();
		if ((b0 | b1) < 0) throw new EOFException();
		return b0 | b1 << 8;
	}

	/**@param max maximum allowed value
	 * @return the next unsigned 16-bit value read from this stream which must be <= max
	 * @throws IOException on I/O error, EOF or value out of range */
	public int readU16(int max) throws IOException {
		return rangeCheck(readU16(), 0, max);
	}

	/**@return the next signed 16-bit value read from this stream
	 * @throws IOException on I/O error or EOF */
	public short readI16() throws IOException {
		return (short)readU16();
	}

	/**@param min minimum allowed value
	 * @param max maximum allowed value
	 * @return the next signed 16-bit value read from this stream which must be in the given range
	 * @throws IOException on I/O error, EOF or value out of range */
	public short readI16(int min, int max) throws IOException {
		return (short)rangeCheck((short)readU16(), min, max);
	}

	/**@return the next signed 32-bit value read from this stream
	 * @throws IOException on I/O error or EOF */
	public int readI32() throws IOException {
		int b0 = in.read(), b1 = in.read(), b2 = in.read(), b3 = in.read();
		if ((b0 | b1 | b2 | b3) < 0) throw new EOFException();
		return b0 | b1 << 8 | b2 << 16 | b3 << 24;
	}

	/**@param min minimum allowed value
	 * @param max maximum allowed value
	 * @return the next signed 32-bit value read from this stream which must be in the given range
	 * @throws IOException on I/O error, EOF or value out of range */
	public int readI32(int min, int max) throws IOException {
		return rangeCheck(readI32(), min, max);
	}

	/**@return the next signed 64-bit value read from this stream
	 * @throws IOException on I/O error or EOF */
	public long readI64() throws IOException {
		int b0 = in.read(), b1 = in.read(), b2 = in.read(), b3 = in.read();
		int b4 = in.read(), b5 = in.read(), b6 = in.read(), b7 = in.read();
		if ((b0 | b1 | b2 | b3 | b4 | b5 | b6 | b7) < 0) throw new EOFException();
		return (long)b0  | (long)b1 <<  8 | (long)b2 << 16 | (long)b3 << 24
		| (long)b4 << 32 | (long)b5 << 40 | (long)b6 << 48 | (long)b7 << 56;
	}

	private final byte[] buf = new byte[255];

	public String readUTF8() throws IOException {
		int l = readVarInt();
		byte[] arr = l <= buf.length ? buf : new byte[l];
		readAll(arr, 0, l);
		return new String(arr, 0, l, UTF_8);
	}

	/**@return an up to 255 byte sized UTF-8 encoded String read from this stream
	 * @throws IOException on I/O error or EOF */
	public String readL8UTF8() throws IOException {
		int l = readU8();
		readAll(buf, 0, l);
		return new String(buf, 0, l, UTF_8);
	}

	/**@return an up to 65535 byte sized UTF-8 encoded String read from this stream
	 * @throws IOException on I/O error or EOF */
	public String readL16UTF8() throws IOException {
		int l = readU16();
		byte[] arr = l <= buf.length ? buf : new byte[l];
		readAll(arr, 0, l);
		return new String(arr, 0, l, UTF_8);
	}

	/**@return an unsigned value constructed using a variable number of bytes from this stream depending on size
	 * @throws IOException on I/O error or EOF */
	public int readVarInt() throws IOException {
		int v;
		if ((v = (byte)in.read()) >= 0
			|| (v = v & 0x7f | (byte)in.read() << 7) >= 0
			|| (v = v & 0x3fff | (byte)in.read() << 14) >= 0
			|| (v = v & 0x1fffff | (byte)in.read() << 21) >= 0
			|| (v = v & 0x0fffffff | in.read() << 28) >= 0
		) return v;
		throw new EOFException();
	}

	/**@param max maximum allowed value
	 * @return an unsigned value <= max, constructed using the number of bytes required to represent max.
	 * @throws IOException on I/O error, EOF or value out of range */
	public int readInt(int max) throws IOException {
		int v =
		max <= 0 ? 0 : in.read() | (
			max <= 0xff ? 0 : in.read() << 8 | (
				max <= 0xffff ? 0 : in.read() << 16 | (
					max <= 0xffffff ? 0 : in.read() << 24
				)
			)
		);
		if (v < 0) throw new EOFException();
		return rangeCheck(v, 0, max);
	}

	private static int rangeCheck(int v, int min, int max) throws IOException {
		if (v < min || v > max)
			throw new IOException("read value %d is out of range [%d..%d]".formatted(v, min, max));
		return v;
	}

}
