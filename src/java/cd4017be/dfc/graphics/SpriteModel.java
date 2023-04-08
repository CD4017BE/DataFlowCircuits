package cd4017be.dfc.graphics;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;

import java.nio.ByteBuffer;
import java.util.Arrays;

import cd4017be.util.AtlasSprite;

/**
 * 
 * @author CD4017BE */
public class SpriteModel {

	private static final short[] EMPTY = new short[0];

	/** registered icon */
	public AtlasSprite icon;
	/** repeated region */
	public int rx0 = 4, ry0 = 4, rx1 = 12, ry1 = 12;
	/** text region */
	public int tx0 = 4, ty0 = 4, tx1 = 12, ty1 = 12;
	/** IO pin positions as (x & 0xff | (y & 0xff) << 8),
	 * where negative x / y mean relative to right / bottom edge. */
	public short[] ins = EMPTY, outs = EMPTY;

	public int tx() {
		return (tx0 + tx1 >> 2) - icon.w;
	}

	public int ty() {
		return ty0 >> 1;
	}

	public int tw() {
		return icon.w - (tx1 - tx0 >> 2);
	}

	public int th() {
		return icon.h - (ty1 - ty0 >> 2);
	}

	public int rh() {
		return ry1 - ry0 >> 2;
	}

	public static final byte[] TGA_SIGNATURE = "TRUEVISION-XFILE.\0".getBytes(US_ASCII);
	public static final int MODEL_TAG = 0x0dfc;

	public boolean readFromImageMetadata(byte[] image) {
		try {
			int l = image.length;
			if (!Arrays.equals(image, l - TGA_SIGNATURE.length, l, TGA_SIGNATURE, 0, TGA_SIGNATURE.length))
				return false;
			ByteBuffer buf = ByteBuffer.wrap(image).order(LITTLE_ENDIAN);
			int ofs = buf.getInt(l - 22), len, tag;
			if (ofs <= 0) return false;
			int n = buf.position(ofs).getChar();
			do {
				if (--n < 0) return false;
				tag = buf.getShort();
				ofs = buf.getInt();
				len = buf.getInt();
			} while(tag != MODEL_TAG);
			buf.position(ofs).limit(ofs + len);
			rx0 = buf.get() & 0xff;
			ry0 = buf.get() & 0xff;
			rx1 = (buf.get() & 0xff) + 1;
			ry1 = (buf.get() & 0xff) + 1;
			tx0 = buf.get() & 0xff;
			ty0 = buf.get() & 0xff;
			tx1 = (buf.get() & 0xff) + 1;
			ty1 = (buf.get() & 0xff) + 1;
			ins = new short[buf.get() & 0xff];
			outs = new short[buf.get() & 0xff];
			for (int i = 0; i < ins.length; i++)
				ins[i] = buf.getShort();
			for (int i = 0; i < outs.length; i++)
				outs[i] = buf.getShort();
		} catch (RuntimeException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public byte[] writeImageMetadata() {
		ByteBuffer buf = ByteBuffer.allocate(10 + (ins.length + outs.length) * 2).order(LITTLE_ENDIAN);
		buf.put((byte)rx0);
		buf.put((byte)ry0);
		buf.put((byte)(rx1 - 1));
		buf.put((byte)(ry1 - 1));
		buf.put((byte)tx0);
		buf.put((byte)ty0);
		buf.put((byte)(tx1 - 1));
		buf.put((byte)(ty1 - 1));
		buf.put((byte)ins.length);
		buf.put((byte)outs.length);
		for (short p : ins) buf.putShort(p);
		for (short p : outs) buf.putShort(p);
		return buf.array();
	}

}
