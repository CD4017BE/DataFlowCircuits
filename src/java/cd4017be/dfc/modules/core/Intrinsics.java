package cd4017be.dfc.modules.core;

import static cd4017be.dfc.lang.Value.NO_DATA;
import static cd4017be.dfc.lang.Value.NO_ELEM;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.NodeContext;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Type;
import cd4017be.dfc.lang.Value;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Impl;
import cd4017be.dfc.lang.instructions.IntrinsicLoader.Init;

/**
 * @author cd4017be */
public class Intrinsics {

	public static Type VOID, INT, FLOAT, STRING;
	public static Value NULL;

	@Init
	public static void init(Module m) {
		VOID = m.types.get("void");
		INT = m.types.get("int");
		FLOAT = m.types.get("float");
		STRING = m.types.get("string");
		NULL = new Value(VOID, NO_ELEM, NO_DATA, 0);
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

	// helper functions

	public static Value parse(String s, NodeContext context, int idx, String name) throws SignalError {
		if (s.isBlank()) return null;
		try {
			CharBuffer buf = CharBuffer.wrap(s);
			Value val = parse(buf, context.def.module);
			if (buf.hasRemaining()) throw new IllegalArgumentException("unexpected symbols " + buf);
			return val;
		} catch (RuntimeException e) {
			throw new SignalError(idx, "can't parse " + name + ": " + e.getMessage(), e);
		}
	}

	private static Value parse(CharBuffer s, Module module) {
		skipWhiteSpace(s);
		if (!s.hasRemaining()) throw new IllegalArgumentException("end of expression");
		char c = s.get(s.position());
		if (Character.isDigit(c) || "+-.".indexOf(c) >= 0) {
			StringBuilder sb = new StringBuilder();
			while(s.hasRemaining()) {
				c = s.get();
				if (Character.isLetterOrDigit(c) || "+-.".indexOf(c) >= 0)
					sb.append(c);
				else if (c != '_') {
					s.position(s.position() - 1);
					break;
				}
			}
			skipWhiteSpace(s);
			String num = sb.toString();
			boolean hex = num.startsWith("0x") || num.startsWith("0X");
			if (!num.contains("NaN") && !num.contains("Infinity"))
				if (num.indexOf('.') < 0 && (hex
					? num.indexOf('p') < 0 && num.indexOf('P') < 0
					: num.indexOf('e') < 0 && num.indexOf('E') < 0
				)) return new Value(INT, NO_ELEM, NO_DATA, hex ? Long.parseLong(num.substring(2), 16) : Long.parseLong(num));
			return new Value(cd4017be.dfc.modules.numext.Intrinsics.FLOAT, NO_ELEM, NO_DATA, Double.doubleToLongBits(Double.parseDouble(num)));
		} else if (c == '"' || c == '\'') {
			ByteBuffer buf = ByteBuffer.allocate(16);
			CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder();
			int p = s.position() + 1, q = p, l = s.limit();
			for(;;) {
				if (q >= l) throw new IllegalArgumentException("end of expression");
				char c1 = s.get(q);
				if (c1 == '\\') {
					buf = encode(enc, s, p, q, buf, false);
					if (++q >= l-1) throw new IllegalArgumentException("end of expression");
					switch(c1 = s.get(q)) {
					case 'n' -> buf.put((byte)'\n');
					case 'r' -> buf.put((byte)'\r');
					case 't' -> buf.put((byte)'\t');
					case '"' -> buf.put((byte)'"');
					case '\'' -> buf.put((byte)'\'');
					case '\\' -> buf.put((byte)'\\');
					//case 'u' -> {} //unicode
					default -> {
						int d = Character.digit(c1, 16) << 4 | Character.digit(s.get(++q), 16);
						if (d < 0) throw new IllegalArgumentException("invalid escape sequence \\" + c1 + s.get(q));
						buf.put((byte)d);
					}}
					p = ++q;
				} else if (c1 == c) {
					buf = encode(enc, s, p, q, buf, true);
					q++;
					break;
				} else q++;
			}
			s.limit(l).position(q);
			byte[] arr = new byte[buf.position()];
			buf.get(0, arr);
			return new Value(STRING, NO_ELEM, arr, 0);
		} else if (c == '#') {
			s.get();
			ByteBuffer buf = ByteBuffer.allocate(16);
			for(;;) {
				if (!s.hasRemaining())
					throw new IllegalArgumentException("end of expression");
				if (Character.isWhitespace(c = s.get())) continue;
				if (c == '#') break;
				if (!s.hasRemaining())
					throw new IllegalArgumentException("end of expression");
				int d = Character.digit(c, 16) << 4 | Character.digit(s.get(), 16);
				if (d < 0) throw new IllegalArgumentException("invalid hex byte: " + c + s.get(s.position() - 1));
				if (!buf.hasRemaining()) buf = grow(buf);
				buf.put((byte)d);
			}
			skipWhiteSpace(s);
			byte[] arr = new byte[buf.position()];
			buf.get(0, arr);
			return new Value(STRING, NO_ELEM, arr, 0);
		} else if (c == '(') {
			s.get();
			ArrayList<Value> values = new ArrayList<>();
			while(s.hasRemaining() && (c = s.get()) != ')') {
				if (c != ',') s.position(s.position() - 1);
				values.add(parse(s, module));
			}
			skipWhiteSpace(s);
			return values.isEmpty() ? NULL : new Value(VOID, values.toArray(Value[]::new), NO_DATA, 0);
		} else if (Character.isJavaIdentifierStart(c)) {
			s.mark();
			while(s.hasRemaining())
				if (!Character.isJavaIdentifierPart(s.get())) {
					s.position(s.position() - 1);
					break;
				}
			int l = s.limit();
			String key = s.limit(s.position()).reset().toString();
			skipWhiteSpace(s.position(s.limit()).limit(l));
			return module.signal(key);
		} else throw new IllegalArgumentException("unexpected symbols " + s.toString());
	}

	private static ByteBuffer grow(ByteBuffer buf) {
		return ByteBuffer.allocate(buf.capacity() * 2).put(buf.flip());
	}

	private static ByteBuffer encode(CharsetEncoder enc, CharBuffer s, int p, int q, ByteBuffer buf, boolean end) {
		if (q > p) {
			int l = s.limit();
			s.limit(q).position(p);
			while(enc.encode(s, buf, end).isOverflow())
				buf = grow(buf);
			s.limit(l);
		}
		return end || buf.hasRemaining() ? buf : grow(buf);
	}

	private static void skipWhiteSpace(CharBuffer s) {
		while(s.hasRemaining())
			if (!Character.isWhitespace(s.get())) {
				s.position(s.position() - 1);
				return;
			}
	}

}
