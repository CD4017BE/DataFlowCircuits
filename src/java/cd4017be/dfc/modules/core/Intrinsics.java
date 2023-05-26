package cd4017be.dfc.modules.core;

import static cd4017be.dfc.lang.Value.NO_DATA;
import static cd4017be.dfc.lang.Value.NO_ELEM;
import static cd4017be.dfc.modules.numext.Intrinsics.FLOAT;
import static modules.loader.Intrinsics.*;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.Module;

/**
 * 
 * @author CD4017BE */
public class Intrinsics {
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
			return new Value(FLOAT, NO_ELEM, NO_DATA, Double.doubleToLongBits(Double.parseDouble(num)));
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
			return values.isEmpty() ? NULL : Value.of(values.toArray(Value[]::new), VOID);
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
			//TODO re-implement module signals
			return NULL;
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
