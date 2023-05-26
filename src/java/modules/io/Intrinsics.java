package modules.io;

import static cd4017be.dfc.lang.Value.NO_DATA;
import static cd4017be.dfc.lang.Value.NO_ELEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import cd4017be.dfc.lang.ArgumentParser;
import cd4017be.dfc.lang.BlockDesc;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.NodeContext;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Type;
import cd4017be.dfc.lang.Value;
import cd4017be.dfc.lang.instructions.ConstantIns;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Impl;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Init;

public class Intrinsics {

	public static Type FILE, INT;

	private static ArgumentParser FILE_MODE = new ArgumentParser() {
		@Override
		public Node parse(
			String arg, BlockDesc block, int argidx, NodeContext context, int idx
		) throws SignalError {
			int m = arg.length() == 1 ? switch(arg.charAt(0)) {
				case 'r' -> 0;
				case 'w' -> 1;
				case 'a' -> 2;
				default -> -1;
			} : -1;
			if (m < 0) throw new SignalError(idx, "invalid file open mode");
			return ConstantIns.node(Value.of(m, INT), idx);
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list, NodeContext context
		) {
			list.add("r");
			list.add("w");
			list.add("a");
		}
	};

	@Init(phase = Init.PRE)
	public static void preInit(Module m) {
		m.parsers.put("filemode", FILE_MODE);
	}

	@Init(phase = Init.POST)
	public static void postInit(Module m) {
		FILE = m.types.get("file");
		INT = modules.loader.Intrinsics.INT;
	}

	//File operations:

	@Impl(inputs = 2, useIp = true)
	public static Value fileOpen(Interpreter ip, int mode, byte[] path) {
		try {
			String pathstr = new String(path, UTF_8);
			Closeable r = switch(mode) {
			case 0 -> new FileInputStream(pathstr);
			case 1, 2 -> new FileOutputStream(pathstr, mode == 2);
			default -> throw new IllegalArgumentException("invalid open mode");
			};
			return new Value(FILE, NO_ELEM, NO_DATA, ip.addResource(r));
		} catch(IOException e) {
			return new Value(FILE, NO_ELEM, e.toString().getBytes(UTF_8), -1);
		}
	}

	@Impl(inputs = 4, useIp = true)
	public static Value fileRead(Interpreter ip, Value file, byte[] data, int ofs, int len) {
		if (!(ip.getResource(file.value) instanceof InputStream is)) return file;
		try {
			is.readNBytes(data, ofs, len);
			return file;
		} catch(IOException e) {
			try {
				ip.removeResource(file.value).close();
			} catch(IOException e1) {e1.printStackTrace();}
			return new Value(FILE, NO_ELEM, e.getMessage().getBytes(UTF_8), -1);
		}
	}

	@Impl(inputs = 4, useIp = true)
	public static Value fileWrite(Interpreter ip, Value file, byte[] data, int ofs, int len) {
		if (!(ip.getResource(file.value) instanceof OutputStream os)) return file;
		try {
			os.write(data, ofs, len);
			return file;
		} catch(IOException e) {
			try {
				ip.removeResource(file.value).close();
			} catch(IOException e1) {e1.printStackTrace();}
			return new Value(FILE, NO_ELEM, e.getMessage().getBytes(UTF_8), -1);
		}
	}

	@Impl(inputs = 1, useIp = true, outType = "INT")
	public static long fileLen(Interpreter ip, long file) {
		Closeable r = ip.getResource(file);
		FileChannel fc;
		if (r instanceof FileInputStream is) fc = is.getChannel();
		else if (r instanceof FileOutputStream os) fc = os.getChannel();
		else return 0;
		try {
			return fc.size() - fc.position();
		} catch(IOException e) {
			return 0;
		}
	}

	@Impl(inputs = 1, useIp = true, outType = "INT")
	public static long fileClose(Interpreter ip, long file) {
		Closeable c = ip.removeResource(file);
		if (c != null) try {
			c.close();
			return 0;
		} catch(IOException e) {
			e.printStackTrace();
		}
		return -1;
	}

}
