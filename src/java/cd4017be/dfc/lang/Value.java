package cd4017be.dfc.lang;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author cd4017be */
public class Value {

	public static final Value[] NO_ELEM = {};
	public static final byte[] NO_DATA = {};

	public final Type type;
	public final Value[] elements;
	public final byte[] data;
	public final long value;

	public Value(Type type, Value[] elements, byte[] data, long value) {
		this.type = type;
		this.elements = elements;
		this.data = data;
		this.value = value;
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + elements.hashCode();
		result = 31 * result + data.hashCode();
		result = 31 * result + Long.hashCode(value);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj
		|| obj instanceof Value other
		&& type == other.type
		&& value == other.value
		&& elements == other.elements
		&& data == other.data;
	}

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
		.append(type.id).append(' ');
		if (value != 0) sb.append(value);
		if (elements.length > 0) {
			sb.append('[').append(elements[0].type.id);
			for (int i = 1; i < elements.length; i++)
				sb.append('|').append(elements[i].type.id);
			sb.append(']');
		}
		if (data.length > 0) {
			sb.append('"');
			for (byte b : data) {
				if (b < 32)
					sb.append('\\').append(HEX[b >> 4 & 15]).append(HEX[b & 15]);
				else if (b == '\\' || b == '"')
					sb.append('\\').append((char)b);
				else sb.append((char)b);
			}
			sb.append('"');
		}
		return sb.toString();
	}

	public String dataAsString() {
		return new String(data, UTF_8);
	}

	public static Value of(Type type) {
		return new Value(type, NO_ELEM, NO_DATA, 0);
	}

	public static Value of(Value[] elements, Type type) {
		return new Value(type, elements, NO_DATA, 0);
	}

	public static Value of(byte[] data, Type type) {
		return new Value(type, NO_ELEM, data, 0);
	}

	public static Value of(long value, Type type) {
		return new Value(type, NO_ELEM, NO_DATA, value);
	}

	public static Value of(String data, Type type) {
		return of(data == null || data.isEmpty() ? NO_DATA : data.getBytes(UTF_8), type);
	}

}
