package cd4017be.util;

import static java.lang.Integer.numberOfLeadingZeros;
import static java.lang.Integer.parseInt;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import cd4017be.dfc.lang.BlockDef;

/**
 * @author CD4017BE */
public class ImageConverter {

	private static final Pattern ENTRY = Pattern.compile("\\s*([^\\s]+)\\s+(?:(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+)?");
	private static final Pattern PIN_POS = Pattern.compile("\\(\\s*(\\-?\\d+)\\s+(\\-?\\d+)\\s*\\)");

	public static void convert(InputStream src, OutputStream dst) throws IOException {
		BufferedImage bi = ImageIO.read(src);
		int w = bi.getWidth(), h = bi.getHeight(), l = w * h;
		//convert to BGRA_5_5_5_1 format
		ByteBuffer pixels = ByteBuffer.allocate(l * 2).order(LITTLE_ENDIAN);
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int c = bi.getRGB(x, y);
				pixels.putShort((short)(
					c >>> 3 & 0x1f | c >>> 6 & 0x3e0 | c >>> 9 & 0x7c00 | c >>> 16 & 0x8000
				));
			}
		pixels.flip();
		try {
			//create color palette
			ByteBuffer palette = ByteBuffer.allocate(Math.min(512, l * 2)).order(LITTLE_ENDIAN);
			byte[] indices = new byte[l];
			for (int i = 0, j; i < l; i++) {
				short c = pixels.getShort();
				for (j = 0; j < palette.position(); j+=2)
					if (palette.getShort(j) == c) break;
				if (j == palette.position())
					palette.putShort(c); //may throw BufferOverflowException
				indices[i] = (byte)(j >> 1);
			}
			//write header
			dst.write(0b10_0111_01);
			dst.write(w - 1);
			dst.write(h - 1);
			//write palette
			int p = (palette.position() >> 1) - 1;
			dst.write(p);
			dst.write(palette.array(), 0, palette.position());
			//write indices
			int bits = 32 - numberOfLeadingZeros(p);
			int d = 0;
			for (int i = 0, b = 0; i < l; i++) {
				d |= (indices[i] & 0xff) << b;
				if ((b += bits) > 8) {
					dst.write(d);
					d >>>= 8; b -= 8;
				}
			}
			dst.write(d);
		} catch(BufferOverflowException e) {
			//palette too big -> save raw
			dst.write(0b00_0111_01);
			dst.write(w - 1);
			dst.write(h - 1);
			dst.write(pixels.array());
		}
	}

	private static boolean processDefs(File src, File dir) {
		try(BufferedReader r = new BufferedReader(new FileReader(new File(src, "layouts.txt")))) {
			ArrayList<Integer> points = new ArrayList<>();
			int l = 0;
			for (String s; (s = r.readLine()) != null; l++) {
				if (s.isBlank()) continue;
				Matcher m = ENTRY.matcher(s);
				if (!m.lookingAt()) {
					System.err.printf("invalid entry in line %d\n", l);
					continue;
				}
				BlockDef def = new BlockDef(m.group(1));
				if (m.start(2) >= 0) {
					def.hasText = true;
					def.textL0 = (byte)parseInt(m.group(2));
					def.textX = (byte)parseInt(m.group(3));
					def.textY = (byte)parseInt(m.group(4));
				}
				m.usePattern(PIN_POS);
				while(m.find()) points.add(
					parseInt(m.group(1)) & 0xff | (parseInt(m.group(2)) & 0xff) << 8
				);
				if (points.isEmpty()) {
					System.err.printf("missing pin coordinates in line %d\n", l);
					continue;
				}
				def.ports = new byte[points.size() * 2];
				for (int i = 0; i < points.size(); i++) {
					int v = points.get(i);
					def.ports[i*2] = (byte)v;
					def.ports[i*2+1] = (byte)(v >> 8);
				}
				points.clear();
				convert(new File(src, def.name + ".png"), new File(dir, def.name + ".im"), def);
			}
			return true;
		} catch(FileNotFoundException e) {
			return false;
		} catch(IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private static void convert(File src, File dst, BlockDef def) {
		System.out.printf("converting: %s\n", src.getName());
		try(
			FileInputStream in = new FileInputStream(src);
			FileOutputStream out = new FileOutputStream(dst)
		) {
			convert(in, out);
			in.close();
			if (def != null) def.writeLayout(out);
			out.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	/**@param src file or directory to convert
	 * @param dst output parent directory */
	private static void convert(File src, File dst) {
		String name = src.getName();
		if (src.isDirectory()) {
			(dst = new File(dst, name)).mkdir();
			if (processDefs(src, dst)) return;
			for (File f : src.listFiles()) convert(f, dst);
		} else {
			int i = name.lastIndexOf('.');
			if (i < 0) return;
			convert(src, new File(dst, name.substring(0, i).concat(".im")), null);
		}
	}

	public static void main(String[] args) {
		convert(
			new File("./src/resources/textures"),
			new File("./bin")
		);
	}

}
