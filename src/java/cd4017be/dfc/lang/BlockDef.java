package cd4017be.dfc.lang;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.*;

import cd4017be.util.AtlasSprite;

/**Definitions of intrinsic blocks.
 * @author CD4017BE */
public class BlockDef {
	private static final byte[] NO_PINS = {0, 0};
	private static final String[] NO_NAMES = {"out"};
	/** unique name */
	public final String name;
	/** text argument macro string */
	public String textMacro;
	/** text argument layout */
	public byte textL0, textX, textY;
	/** relative I/O pin coordinates as (x, y) pairs. */
	public byte[] ports = NO_PINS;
	/** I/O pin names */
	public String[] ioNames = NO_NAMES;
	/** performs compile time type evaluation */
	public ITypeEvaluator eval;
	/** compiles this block */
	public MethodHandle compiler;
	/** icon for display in editor */
	public AtlasSprite icon;
	/** documentation text */
	public String description = "";

	public BlockDef(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}

	public int ios() {
		return ports.length >> 1;
	}

	public void readLayout(InputStream in) throws IOException {
		int n = in.read();
		if (n < 0) throw new EOFException();
		ioNames = new String[++n];
		in.readNBytes(ports = new byte[n *= 2], 0, n);
		n = in.read() | in.read() << 8;
		if (n < 0) throw new EOFException();
		textX = (byte)(n & 15);
		textY = (byte)(n >> 4 & 15);
		textL0 = (byte)(n >> 8 & 15);
		if ((n & 0x8000) != 0)
			if ((n = in.read()) >= 0) //backwards compatibility
				textMacro = new String(in.readNBytes(n), UTF_8);
			else textMacro = "";
		else textMacro = null;
		for (int i = 0; i < ioNames.length; i++)
			ioNames[i] = (n = in.read()) >= 0 //backwards compatibility
				? new String(in.readNBytes(n), UTF_8)
				: i == 0 ? "out" : "in" + i;
		description = (n = in.read() | in.read() << 8) < 0 ? "" //backwards compatibility
			: new String(in.readNBytes(n), UTF_8);
	}

	public void writeLayout(OutputStream out) throws IOException {
		out.write(ios() - 1);
		out.write(ports);
		out.write(textX | textY << 4);
		out.write(textL0 | (textMacro != null ? 0x80 : 0));
		if (textMacro != null) {
			byte[] arr = textMacro.getBytes(UTF_8);
			out.write(arr.length);
			out.write(arr);
		}
		for (String name : ioNames) {
			byte[] arr = name.getBytes(UTF_8);
			out.write(arr.length);
			out.write(arr);
		}
		byte[] arr = description.getBytes(UTF_8);
		out.write(arr.length);
		out.write(arr.length >> 8);
		out.write(arr);
	}

	public BlockDef withPins(int n) {
		System.err.printf(
			"warning: block %s loaded with %d inputs but defined with %d\n",
			name, n, ioNames.length
		);
		BlockDef alt = new BlockDef(name);
		alt.compiler = compiler;
		alt.eval = eval;
		alt.textMacro = textMacro;
		alt.icon = icon;
		alt.textL0 = textL0;
		alt.textX = textX;
		alt.textY = textY;
		alt.ports = ports;
		alt.ioNames = Arrays.copyOf(ioNames, n);
		alt.description = description;
		return alt;
	}

	/**Name of the final program end block. */
	public static final String OUT_ID = "out";

	private static final Set<String> javaKeywords = Set.of(
		"void", "boolean", "byte", "short", "char", "int", "long", "float", "double",
		"private", "public", "protected", "default", "static", "final", "const", "new",
		"throws", "class", "interface", "enum", "abstract", "import", "package", "volatile",
		"strictfp", "if", "do", "while", "for", "goto", "break", "continue", "try", "catch",
		"return", "switch", "case", "else"
	);

	/**Assigns compile handlers to block definitions based on static methods in given class.
	 * @param defs map of of known block types.
	 * @param c class providing the compile handlers
	 * @param l Lookup for accessing the static methods in c
	 * @param t type of the compile handler 
	 * @param fallback */
	public static void setCompilers(Map<String, BlockDef> defs, Class<?> c, Lookup l, MethodType t, MethodHandle fallback) {
		for (BlockDef def : defs.values())
			try {
				String name = def.name;
				if (javaKeywords.contains(name)) name = '_' + name;
				else name = name.replace('#', '_');
				def.compiler = l.findStatic(c, name, t);
			} catch(NoSuchMethodException e) {
				def.compiler = fallback;
			} catch(IllegalAccessException e) {
				throw new RuntimeException(e);
			}
	}

}
