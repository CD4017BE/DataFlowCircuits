package cd4017be.util;

import java.io.*;

/**
 * 
 * @author CD4017BE */
public class ConfigWriter implements Closeable {

	private final Writer w;
	private final String indent;
	private int lvl;
	private boolean space, comma, line;

	public ConfigWriter(Writer w, String indent) {
		this.w = w;
		this.indent = indent;
	}

	public ConfigWriter nl() throws IOException {
		if (comma) w.write(',');
		line = true;
		return this;
	}

	private void space() throws IOException {
		if (line) {
			w.write('\n');
			for (int i = lvl; i > 0; i--)
				w.write(indent);
			line = false;
		} else {
			if (comma) w.write(',');
			if (space) w.write(' ');
		}
		space = comma = true;
	}

	public ConfigWriter val(String s) throws IOException {
		space();
		char sep = '\'';
		if (s.indexOf(sep) >= 0) sep = '"';
		w.write(sep);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			char esc = switch(c) {
			case '\r' -> 'r';
			case '\n' -> 'n';
			case '\t' -> 't';
			case '\\' -> '\\';
			case '"' -> (sep == c ? c : 0);
			default -> 0;
			};
			if (esc != 0) {
				w.write('\\');
				c = esc;
			}
			w.write(c);
		}
		w.write(sep);
		return this;
	}

	public ConfigWriter val(long v) throws IOException {
		space();
		w.write(Long.toString(v));
		return this;
	}

	public ConfigWriter val(double v) throws IOException {
		space();
		w.write(Double.toString(v));
		return this;
	}

	public ConfigWriter key(String k) throws IOException {
		space();
		comma = false;
		w.write(k);
		return this;
	}

	public ConfigWriter begin() throws IOException {
		space = false;
		space();
		w.write('(');
		lvl++;
		comma = space = false;
		return this;
	}

	public ConfigWriter end() throws IOException {
		lvl--;
		comma = space = false;
		space();
		w.write(")");
		return this;
	}

	public <T> ConfigWriter optKeyArray(
		String key, T[] arr, Encoder<T> encoder, boolean nl
	) throws IOException {
		if (arr.length == 0) return this;
		key(key).begin();
		if (nl) nl();
		for (T e : arr) {
			encoder.encode(this, e);
			if (nl) nl();
		}
		return end().nl();
	}

	@Override
	public void close() throws IOException {
		w.close();
	}

	public interface Encoder<T> {
		void encode(ConfigWriter cw, T elem) throws IOException;
	}

}
