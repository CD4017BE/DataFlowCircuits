package cd4017be.dfc.lang;

import static java.lang.Math.max;
import static java.lang.Math.min;

import cd4017be.compiler.NodeAssembler;
import cd4017be.dfc.compiler.NodeCompiler;
import cd4017be.dfc.graph.Behavior;
import cd4017be.util.AtlasSprite;
import cd4017be.util.IconAtlas.IconHolder;

/**Definitions of intrinsic blocks.
 * @author CD4017BE */
public class BlockDef implements IconHolder {
	/** unique name */
	public final String name;
	/** I/O pin counts */
	public final int outCount, inCount;
	public final boolean hasArg;
	/** relative I/O pin coordinates as (x, y) pairs, followed by text (x, y, len) */
	public final byte[] pins;
	/** I/O pin names */
	public final String[] ioNames;
	/** */
	public NodeAssembler content;
	/** compiles this block */
	public NodeCompiler compiler;
	/** performs compile time type evaluation */
	public Behavior behavior;
	/** icon for display in editor */
	public AtlasSprite icon;
	/** documentation text */
	public String shortDesc = "", longDesc = "";
	public final byte addIn, addOut;

	public BlockDef(String name, int out, int in, boolean arg) {
		this.name = name;
		this.outCount = out;
		this.inCount = in;
		this.hasArg = arg;
		this.pins = new byte[(out + in) * 2 + (arg ? 3 : 0)];
		this.ioNames = new String[out + in + (arg ? 1 : 0)];
		this.addIn = (byte)(IN_ID.equals(name) ? 1 : 0);
		this.addOut = (byte)(OUT_ID.equals(name) ? 1 : 0);
	}

	@Override
	public String toString() {
		return name;
	}

	public int ios() {
		return outCount + inCount;
	}

	public int textX(String arg) {
		int l = pins.length;
		return pins[l - 3] + max(0, pins[l - 1] - arg.length());
	}

	public int textY() {
		return pins[pins.length - 2];
	}

	public int stretch(String arg) {
		return hasArg ? Math.max(0, arg.length() - pins[pins.length - 1]) : 0;
	}

	public BlockDef withIO(int out, int in, boolean arg) {
		if (outCount == out && inCount == in && hasArg == arg) return this;
		System.err.printf("warning: block %s loaded with different IO than definition\n", name);
		return copyTo(new BlockDef(name, out, in, arg));
	}

	public BlockDef copyTo(BlockDef def) {
		if (def == this || behavior == null) return def;
		int l = min(def.outCount, outCount);
		System.arraycopy(pins, 0, def.pins, 0, l * 2);
		System.arraycopy(ioNames, 0, def.ioNames, 0, l);
		l = min(def.inCount, inCount);
		System.arraycopy(pins, outCount * 2, def.pins, def.outCount * 2, l * 2);
		System.arraycopy(ioNames, outCount, def.ioNames, def.outCount, l);
		if (def.hasArg && hasArg) {
			System.arraycopy(pins, ios() * 2, def.pins, def.ios() * 2, 3);
			def.ioNames[def.ios()] = ioNames[ios()];
		}
		if (def.outCount != outCount || def.inCount != inCount) {
			def.compiler = NodeCompiler.NULL;
			def.behavior = (node, c) -> {throw new SignalError(node, 0, "outdated API");};
		} else {
			def.compiler = compiler;
			def.behavior =  behavior;
		}
		def.icon = icon;
		def.shortDesc = shortDesc;
		def.longDesc = longDesc;
		return def;
	}

	/**Name of the final program end block. */
	public static final String OUT_ID = "out", IN_ID = "in";

	@Override
	public AtlasSprite icon() {
		return icon;
	}

	@Override
	public void icon(AtlasSprite icon) {
		this.icon = icon;
	}

}
