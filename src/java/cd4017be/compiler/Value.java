package cd4017be.compiler;

import java.io.IOException;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import cd4017be.compiler.builtin.Bundle;
import cd4017be.compiler.builtin.CstBytes;
import cd4017be.compiler.builtin.CstFloat;
import cd4017be.compiler.builtin.CstInt;
import cd4017be.compiler.builtin.ScopeData;

/**
 * 
 * @author CD4017BE */
public class Value implements Instruction {

	public final Type type;

	public Value(Type type) {
		this.type = type;
	}

	protected Value(Type type, boolean check) {
		if (check && type.vtable.valueClass != getClass())
			throw new IllegalArgumentException("wrong type");
		this.type = type;
	}

	public SideEffects effect(SideEffects... args) {
		return new SideEffects(SideEffects.combine(args), null, this);
	}

	@Override
	public String toString() {
		return type.toString();
	}

	public int elCount() {
		return 0;
	}

	public Value element(int i) {
		return null;
	}

	/**@return bytes to serialize this value to a file */
	public CstBytes data() {
		return null;
	}

	static final MethodType DESERIALIZER
	= MethodType.methodType(Value.class, Type.class, byte[].class, Value[].class);

	public static Value deserialize(Type type, byte[] data, Value[] elements) throws IOException {
		if (data == null) return new Value(type);
		try {
			return (Value)type.vtable.deserializer.invokeExact(type, data, elements);
		} catch(Throwable e) {
			throw new IOException(e);
		}
	}

	public static Value parse(String s, NodeContext context) {
		try {
			CharBuffer buf = CharBuffer.wrap(s);
			Value val = parse(buf, context.def.module);
			if (buf.hasRemaining()) throw new IllegalArgumentException("unexpected symbols " + buf);
			return val;
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("can't parse expression: " + e.getMessage());
		}
	}

	public static Value parse(CharBuffer s, Module module) {
		skipWhiteSpace(s);
		if (!s.hasRemaining()) throw new IllegalArgumentException("unexpected end of input");
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
				)) return new CstInt(num);
			return new CstFloat(num);
		} else if (c == '"' || c == '\'') {
			ByteBuffer buf = ByteBuffer.allocate(16);
			CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder();
			int p = s.position() + 1, q = p, l = s.limit();
			for(;;) {
				if (q >= l) throw new IllegalArgumentException("unexpected end of input");
				char c1 = s.get(q);
				if (c1 == '\\') {
					buf = encode(enc, s, p, q, buf, false);
					if (++q >= l-1) throw new IllegalArgumentException("unexpected end of input");
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
			return new CstBytes(buf.array(), 0, buf.position());
		} else if (c == '#') {
			s.get();
			ByteBuffer buf = ByteBuffer.allocate(16);
			for(;;) {
				if (!s.hasRemaining())
					throw new IllegalArgumentException("unexpected end of input");
				if (Character.isWhitespace(c = s.get())) continue;
				if (c == '#') break;
				if (!s.hasRemaining())
					throw new IllegalArgumentException("unexpected end of input");
				int d = Character.digit(c, 16) << 4 | Character.digit(s.get(), 16);
				if (d < 0) throw new IllegalArgumentException("invalid hex byte: " + c + s.get(s.position() - 1));
				if (!buf.hasRemaining()) buf = grow(buf);
				buf.put((byte)d);
			}
			skipWhiteSpace(s);
			return new CstBytes(buf.array(), 0, buf.position());
		} else if (c == '(') {
			s.get();
			ArrayList<Value> values = new ArrayList<>();
			while(s.hasRemaining() && (c = s.get()) != ')') {
				if (c != ',') s.position(s.position() - 1);
				values.add(parse(s, module));
			}
			skipWhiteSpace(s);
			return values.isEmpty() ? Bundle.VOID : new Bundle(values.toArray(Value[]::new));
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
			s.limit(q).position(p);
			while(enc.encode(s, buf, end).isOverflow())
				buf = grow(buf);
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

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		return this;
	}

}
