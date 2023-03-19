package cd4017be.util;

import java.io.*;
import java.util.*;

/**
 * 
 * @author CD4017BE */
public class ConfigFile {

	public static Object[] parse(Reader r) throws IOException {
		try {
			return parseList(r, new ArrayDeque<>(), new StringBuilder(), -1);
		} finally {
			r.close();
		}
	}

	private static Object[] parseList(Reader r, ArrayDeque<Object> stack, StringBuilder sb, int end) throws IOException {
		int i = stack.size(), c = r.read();
		while((c = parse(r, c, stack, sb)) != end) {
			if (c == ',') c = r.read();
		}
		Object[] arr = new Object[stack.size() - i];
		for (i = arr.length - 1; i >= 0; i--)
			arr[i] = stack.pop();
		return arr;
	}

	private static int parse(Reader r, int c, ArrayDeque<Object> stack, StringBuilder sb) throws IOException {
		while(c >= 0 && Character.isWhitespace(c)) c = r.read();
		if (c < 0 || c == ')') return c;
		if (c == '(') {
			stack.push(parseList(r, stack, sb, ')'));
			c = r.read();
		} else if (c == '\'' || c == '"') {
			int end = c;
			while((c = r.read()) != end) {
				if (c == '\n' || c == '\r')
					while((c = r.read()) >= 0 && Character.isWhitespace(c));
				if (c == '\\')
					c = switch(c = r.read()) {
						case 'n' -> '\n';
						case 'r' -> '\r';
						case 't' -> '\t';
						default -> c;
					};
				if (c < 0) throw new IOException("EOF within string literal");
				sb.append((char)c);
			}
			stack.push(sb.toString());
			sb.delete(0, sb.length());
			c = r.read();
		} else if (numberStart(c)) {
			boolean hex = c == '#';
			if (!hex) sb.append((char)c);
			while(numberPart(c = r.read()))
				if (c != '_') sb.append((char)c);
			try {
				stack.push(parseNumber(sb.toString(), hex));
			} catch(NumberFormatException e) {
				throw new IOException(e);
			}
			sb.delete(0, sb.length());
		} else if (identifierStart(c)) {
			sb.append((char)c);
			while(identifierPart(c = r.read()))
				sb.append((char)c);
			String key = sb.toString();
			c = parse(r, c, stack, sb.delete(0, sb.length()));
			stack.push(new KeyValue(key, stack.pop()));
		} else throw new IOException("unexpected " + name(c));
		while(c >= 0 && Character.isWhitespace(c)) c = r.read();
		return c;
	}

	private static Number parseNumber(String s, boolean hex) {
		if (!s.contains("NaN") && !s.contains("Infinity")) {
			if (s.indexOf('.') < 0 && (hex
				? s.indexOf('p') < 0 && s.indexOf('P') < 0
				: s.indexOf('e') < 0 && s.indexOf('E') < 0
			)) return Long.valueOf(s, hex ? 16 : 10);
			if (hex && !s.isEmpty()) {
				char c = s.charAt(0);
				if (c == '-' || c == '+')
					s = c + "0x" + s.substring(1);
				else s = "0x" + s;
			}
		}
		return Double.valueOf(s);
	}

	private static String name(int c) {
		return c < 0 ? "EOF" : "'" + (char)c + "'";
	}

	private static boolean identifierStart(int c) {
		return c >= 0 && Character.isJavaIdentifierStart(c);
	}

	private static boolean identifierPart(int c) {
		return c >= 0 && (Character.isJavaIdentifierPart(c) || "./".indexOf(c) >= 0);
	}

	private static boolean numberStart(int c) {
		return c >= 0 && (Character.isDigit(c) || "#+-.".indexOf(c) >= 0);
	}

	private static boolean numberPart(int c) {
		return c >= 0 && (Character.isLetterOrDigit(c) || "+-._".indexOf(c) >= 0);
	}

	public static record KeyValue(String key, Object value) {
		@Override
		public String toString() {
			return key + " " + (value instanceof Object[] arr ? Arrays.deepToString(arr) : value.toString());
		}
	}

	public static void main(String[] args) throws IOException {
		System.out.println(Arrays.deepToString(parse(new StringReader(
			"blocks(in(icon 'in.png', pins(x(2, 2)), text('name', 2, 2, 1), doc 'Provides a signal from a output block or a macro input.'))"
		))));
	}

}
