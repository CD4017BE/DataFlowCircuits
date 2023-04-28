package cd4017be.util;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;

import cd4017be.dfc.graphics.SpriteModel;
import cd4017be.util.ConfigFile.KeyValue;

/**
 * 
 * @author CD4017BE */
public class SpriteModelConverter {

	public static void main(String[] args) {
		for (String arg : args) {
			if (arg.startsWith("-")) continue;
			try {
				Path file = Path.of(arg, "metadata.cfg");
				FileTime t = Files.getLastModifiedTime(file);
				Object[] cfg = ConfigFile.parse(Files.newBufferedReader(file));
				for (Object e : cfg)
					if (e instanceof KeyValue kv)
						visitIcon(arg, kv, t);
			} catch(IOException e) {
				System.out.printf("can't read metadata for %s : %s\n", arg, e);
			}
		}
	}

	private static void visitIcon(String path, KeyValue kv0, FileTime t) {
		String key = kv0.key();
		Path file = Path.of(path, key + ".tga");
		SpriteModel model = new SpriteModel();
		byte[] data;
		try {
			FileTime t1 = Files.getLastModifiedTime(file);
			data = Files.readAllBytes(file);
			if (model.readFromImageMetadata(data) && t1.compareTo(t) > 0) return;
			for (Object e1 : (Object[])kv0.value()) {
				KeyValue kv1 = (KeyValue)e1;
				switch(kv1.key()) {
				case "out" -> model.outs = parseIO((Object[])kv1.value());
				case "in" -> model.ins = parseIO((Object[])kv1.value());
				case "rep" -> {
					Object[] arr = (Object[])kv1.value();
					model.rx0 = ((Number)arr[0]).intValue();
					model.ry0 = ((Number)arr[1]).intValue();
					model.rx1 = ((Number)arr[2]).intValue();
					model.ry1 = ((Number)arr[3]).intValue();
				}
				case "text" -> {
					Object[] arr = (Object[])kv1.value();
					model.tx0 = ((Number)arr[0]).intValue();
					model.ty0 = ((Number)arr[1]).intValue();
					model.tx1 = ((Number)arr[2]).intValue();
					model.ty1 = ((Number)arr[3]).intValue();
				}
				}
			}
		} catch (IOException e) {
			return;
		} catch(ClassCastException | IndexOutOfBoundsException e) {
			System.out.printf("invalid metadata entry %s : %s\n", file, e);
			return;
		}
		System.out.printf("injecting %s\n", file);
		int len = 18 + U8(data, 0) + U16(data, 5) * (U8(data, 7) + 7 >> 3);
		int n = U16(data, 12) * U16(data, 14), d = U8(data, 16) + 7 >> 3;
		if ((U8(data, 2) & 8) == 0)
			len += n * d;
		else while(n > 0) {
			int r = data[len], l = (r & 127) + 1;
			n -= l;
			len += (r < 0 ? d : d * l) + 1;
		}
		try (OutputStream os = Files.newOutputStream(file)){
			os.write(data, 0, len);
			os.write(data = model.writeImageMetadata());
			os.write(
				ByteBuffer.allocate(20).order(LITTLE_ENDIAN)
				.putInt(0x0dfc0001)
				.putInt(len).putInt(data.length)
				.putInt(0).putInt(len + data.length)
				.array()
			);
			os.write(SpriteModel.TGA_SIGNATURE);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private static int U8(byte[] data, int i) {
		return data[i] & 0xff;
	}

	private static int U16(byte[] data, int i) {
		return data[i] & 0xff | (data[i + 1] & 0xff) << 8;
	}

	private static short[] parseIO(Object[] arr) {
		short[] val = new short[arr.length];
		int i = 0;
		for (Object e : arr) {
			Object[] arr1 = (Object[])e;
			val[i++] = (short)(
				((Number)arr1[0]).intValue() & 0xff
				| ((Number)arr1[1]).intValue() << 8
			);
		}
		return val;
	}

}
