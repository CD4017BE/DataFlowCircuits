package cd4017be.dfc.lang;

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
	/** unique name */
	public final String name;
	/** has_text & 0x8000 | pos_ofs + 4 & 0x7000 | top_pad & 0xf00 | left_pad & 0x0f0 | right_pad & 0x00f */
	public short textSize;
	/** relative I/O pin coordinates as (x, y) pairs. */
	public byte[] ports = NO_PINS;
	/** performs compile time type evaluation */
	public ITypeEvaluator eval;
	/** compiles this block */
	public MethodHandle compiler;
	/** icon for display in editor */
	public AtlasSprite icon;

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
		n = (n + 1) * 2;
		in.readNBytes(ports = new byte[n], 0, n);
		n = in.read() | in.read() << 8;
		if (n < 0) throw new EOFException();
		textSize = (short)n;
	}

	/**Data Format:<pre>
	 * B io_count - 1
	 * io_count * {
	 *   B dx, B dy
	 * }
	 * 4b left_pad, 4b right_pad, 4b top_pad, 3b 0, 1b has_text </pre>
	 * @param out
	 * @throws IOException */
	public void writeLayout(OutputStream out) throws IOException {
		out.write(ios() - 1);
		out.write(ports);
		out.write(textSize);
		out.write(textSize >> 8);
	}

	/**Name of the final program end block. */
	public static final String OUT_ID = "main";

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
