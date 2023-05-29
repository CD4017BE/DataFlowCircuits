package modules.dfc.core;

import static cd4017be.dfc.lang.LoadingCache.LOADER;
import static cd4017be.dfc.lang.Value.NO_ELEM;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static modules.dfc.module.Intrinsics.NULL;

import java.util.*;

import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.builders.*;
import cd4017be.dfc.lang.instructions.*;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Impl;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Init;

/**
 * @author cd4017be */
public class Intrinsics {

	public static Type VOID, INT, STRING;

	public static final NodeAssembler PACK = (block, context, idx) -> {
		if (block.outs() != 1)
			throw new SignalError(idx, "wrong IO count");
		block.makeNode(new PackIns(), idx);
	};
	public static final NodeAssembler DEPEND = (block, context, idx) -> {
		if (block.ins() == 0) throw new SignalError(idx, "wrong IO count");
		Node node = new Node(null, Node.PASS, block.ins(), idx);
		block.setIns(node);
		block.makeOuts(node, idx);
	};
	public static final NodeAssembler LOOP = (block, context, idx) -> {
		if (block.def.ins.length != 2 && block.def.outs.length != 2)
			throw new SignalError(idx, "wrong IO count");
		Node state = new Node(null, Node.BEGIN, 0, idx);
		Node loop = new Node(null, Node.END, 3, idx);
		loop.in[0].connect(state);
		block.setIn(0, loop.in[2], idx);
		block.setIn(1, loop.in[1], idx);
		block.setOut(0, loop, idx);
		block.setOut(1, state, idx);
	};

	public static final ArgumentParser STRING_ARG = (arg, block, argidx, context, idx) ->
		ConstantIns.node(Value.of(parseString(arg, idx), STRING), idx);

	private static byte[] parseString(String s, int idx) throws SignalError {
		if (s.isEmpty()) return Value.NO_DATA;
		byte[] data = s.getBytes(UTF_8);
		int l = 0;
		for (int i = 0; i < data.length; i++, l++) {
			byte b = data[i];
			if (b != '\\') {
				data[l] = b;
				continue;
			}
			if (++i >= data.length)
				throw new SignalError(idx, "incomplete escape at end of string");
			switch(b = data[i]) {
			case '\\' -> data[l] = '\\';
			case '\'' -> data[l] = '\'';
			case '\"' -> data[l] = '\"';
				case 'n' -> data[l] = '\n';
			case 'r' -> data[l] = '\r';
			case 't' -> data[l] = '\t';
			case 'b' -> data[l] = '\b';
			case 'f' -> data[l] = '\f';
			default -> {
				byte b1 = ' ';
				int n;
				if (++i >= data.length || (n = hexChar(b) << 4 | hexChar(b1 = data[i])) < 0)
					throw new SignalError(idx, "invalid escape sequence: \\" + (char)(b & 0xff) + (char)(b1 & 0xff));
				data[l] = (byte)n;
			}}
		}
		return l < data.length ? Arrays.copyOf(data, l) : data;
	}

	private static int hexChar(byte b) {
		if (b >= '0' && b <= '9') return b - '0';
		if (b >= 'a' && b <= 'f') return b - ('a' - 10);
		if (b >= 'A' && b <= 'F') return b - ('A' - 10);
		return -1;
	}

	public static final ArgumentParser INT_ARG = (arg, block, argidx, context, idx) ->
		ConstantIns.node(Value.of(parseInt(arg, idx), INT), idx);

	private static long parseInt(String s, int idx) throws SignalError {
		s = s.replace("_", "");
		if (s.isEmpty()) throw new SignalError(idx, "can't parse empty string to int");
		try {
			char c = s.charAt(0);
			if (c == '+') return Long.parseUnsignedLong(s);
			if (c == '-') return Long.parseLong(s);
			int base = 10, i = 0;
			if (c == '0')
				if (s.length() > 1) c = s.charAt(++i);
				else return 0;
			if (c == 'x' || c == 'X') base = 16;
			else if (c == 'o' || c == 'O') base = 8;
			else if (c == 'b' || c == 'B') base = 2;
			else return Long.parseLong(s);
			return Long.parseUnsignedLong(s, i + 1, s.length(), base);
		} catch (NumberFormatException e) {
			throw new SignalError(idx, e.getMessage());
		}
	}

	@Init
	public static void init(Module m) {
		m.assemblers.put("macro", Macro::new);
		m.assemblers.put("func", Function::new);
		m.assemblers.put("const", ConstList::new);
		m.assemblers.put("swt", SwitchBuilder::new);
		m.assemblers.put("dep", def -> DEPEND);
		m.assemblers.put("pack", def -> PACK);
		m.assemblers.put("loop", def -> LOOP);
		m.parsers.put("str", STRING_ARG);
		m.parsers.put("int", INT_ARG);
		VOID = LOADER.getType("void");
		STRING = LOADER.getType("string");
		INT = LOADER.getType("int");
	}

	@Impl(inputs = 2, outType = "VOID")
	public static long swt(long path, long count) {
		return path >= 0 && path < count ? path + 1 : 0;
	}

	//type operations:

	@Impl(inputs = 4)
	public static Value signal(Type type, Value[] elements, byte[] data, long value) {
		return new Value(type, elements, data, value);
	}

	@Impl(inputs = 2, outType = "INT")
	public static long typeEqual(Type a, Type b) {
		return a == b ? -1 : 0;
	}

	@Impl(inputs = 1, outType = "STRING")
	public static byte[] typeToStr(Type type) {
		return type.id.getBytes(UTF_8);
	}

	//element operations:

	@Impl(inputs = 1, outType = "VOID")
	public static Value[] elemNew(int len) {
		if (len == 0) return NO_ELEM;
		Value[] arr = new Value[len];
		for (int i = 0; i < len; i++) arr[i] = NULL;
		return arr;
	}

	@Impl(inputs = 1, outType = "INT")
	public static int elemLen(Value[] arr) {
		return arr.length;
	}

	@Impl(inputs = 2)
	public static Value elemGet(Value[] arr, int idx) {
		return arr[idx];
	}

	@Impl(inputs = 3)
	public static void elemSet(Value[] arr, int idx, Value val) {
		arr[idx] = val;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long elemEqual(Value[] a, Value[] b) {
		return a == b ? -1 : 0;
	}

	@Impl(inputs = 5)
	public static void elemCopy(Value[] dst, int dstOfs, Value[] src, int srcOfs, int len) {
		System.arraycopy(src, srcOfs, dst, dstOfs, len);
	}

	//data operations:

	@Impl(inputs = 1, outType = "STRING")
	public static byte[] dataNew(int len) {
		return new byte[len];
	}

	@Impl(inputs = 1, outType = "INT")
	public static int dataLen(byte[] data) {
		return data.length;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long dataRead1(byte[] data, int idx) {
		return (long)(data[idx] & 0xff);
	}

	@Impl(inputs = 2, outType = "INT")
	public static long dataRead2(byte[] data, int idx) {
		return (long)(data[idx] & 0xff) | (long)(data[idx+1] & 0xff) << 8;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long dataRead4(byte[] data, int idx) {
		return
			(long)(data[idx  ] & 0xff)       | (long)(data[idx+1] & 0xff) <<  8 |
			(long)(data[idx+2] & 0xff) << 16 | (long)(data[idx+3] & 0xff) << 24;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long dataRead8(byte[] data, int idx) {
		return
			(long)(data[idx  ] & 0xff)       | (long)(data[idx+1] & 0xff) <<  8 |
			(long)(data[idx+2] & 0xff) << 16 | (long)(data[idx+3] & 0xff) << 24 |
			(long)(data[idx+4] & 0xff) << 32 | (long)(data[idx+5] & 0xff) << 40 |
			(long)(data[idx+6] & 0xff) << 48 | (long)(data[idx+7] & 0xff) << 56;
	}

	@Impl(inputs = 3)
	public static void dataWrite1(byte[] data, int idx, long val) {
		data[idx] = (byte)val;
	}

	@Impl(inputs = 3)
	public static void dataWrite2(byte[] data, int idx, long val) {
		data[idx] = (byte)val; data[idx+1] = (byte)(val >> 8);
	}

	@Impl(inputs = 3)
	public static void dataWrite4(byte[] data, int idx, long val) {
		data[idx  ] = (byte) val       ; data[idx+1] = (byte)(val >>  8);
		data[idx+2] = (byte)(val >> 16); data[idx+3] = (byte)(val >> 24);
	}

	@Impl(inputs = 3)
	public static void dataWrite8(byte[] data, int idx, long val) {
		data[idx  ] = (byte) val       ; data[idx+1] = (byte)(val >>  8);
		data[idx+2] = (byte)(val >> 16); data[idx+3] = (byte)(val >> 24);
		data[idx+4] = (byte)(val >> 32); data[idx+5] = (byte)(val >> 40);
		data[idx+6] = (byte)(val >> 48); data[idx+7] = (byte)(val >> 56);
	}

	@Impl(inputs = 2, outType = "INT")
	public static long dataEqual(byte[] a, byte[] b) {
		return a == b ? -1 : 0;
	}

	@Impl(inputs = 5)
	public static void dataCopy(byte[] dst, int dstOfs, byte[] src, int srcOfs, int len) {
		System.arraycopy(src, srcOfs, dst, dstOfs, len);
	}

	@Impl(inputs = 2, outType = "INT")
	public static int dataComp(byte[] a, byte[] b) {
		return Arrays.compare(a, b);
	}

	//int operations:

	@Impl(inputs = 1, outType = "INT")
	public static long strToInt(byte[] str) {
		return Long.parseLong(new String(str, US_ASCII));
	}

	@Impl(inputs = 1, outType = "STRING")
	public static byte[] intToStr(long val) {
		return Long.toString(val).getBytes();
	}

	@Impl(inputs = 2, outType = "INT")
	public static long add(long a, long b) {
		return a + b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long sub(long a, long b) {
		return a - b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long mul(long a, long b) {
		return a * b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long idiv(long a, long b) {
		return a / b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long imod(long a, long b) {
		return a % b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long and(long a, long b) {
		return a & b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long or(long a, long b) {
		return a | b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long xor(long a, long b) {
		return a ^ b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long shl(long a, long b) {
		return a << b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static long ishr(long a, long b) {
		return a >> b;
	}

	@Impl(inputs = 2, outType = "INT")
	public static int icomp(long a, long b) {
		return Long.compare(a, b);
	}

	@Impl(inputs = 1, outType = "INT")
	public static long lt0(long v) {
		return v < 0 ? -1 : 0;
	}

	@Impl(inputs = 1, outType = "INT")
	public static long gt0(long v) {
		return v > 0 ? -1 : 0;
	}

	@Impl(inputs = 1, outType = "INT")
	public static long le0(long v) {
		return v <= 0 ? -1 : 0;
	}

	@Impl(inputs = 1, outType = "INT")
	public static long ge0(long v) {
		return v >= 0 ? -1 : 0;
	}

	@Impl(inputs = 1, outType = "INT")
	public static long eq0(long v) {
		return v == 0 ? -1 : 0;
	}

	@Impl(inputs = 1, outType = "INT")
	public static long ne0(long v) {
		return v != 0 ? -1 : 0;
	}

	@Impl(inputs = 3)
	public static Value sel(long sel, Value a, Value b) {
		return sel < 0 ? b : a;
	}

}
