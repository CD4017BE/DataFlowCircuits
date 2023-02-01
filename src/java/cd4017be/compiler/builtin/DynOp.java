package cd4017be.compiler.builtin;

import java.util.Arrays;

import cd4017be.compiler.*;


/**
 * @author CD4017BE */
public class DynOp extends Bundle {

	private static final CstBytes TRUE = new CstBytes(new byte[] {1}), FALSE = new CstBytes(new byte[] {0});

	public int uses;
	public final boolean isGlobal;

	public DynOp(Type type, Value[] args, boolean isGlobal) {
		super(type, args);
		this.isGlobal = isGlobal;
		for (Value v : args)
			if (v instanceof DynOp o)
				o.uses++;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(values) * 31 + type.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof DynOp other
		&& this.type == other.type
		&& Arrays.equals(this.values, other.values);
	}

	@Override
	public CstBytes data() {
		return isGlobal ? TRUE : FALSE;
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		return new DynOp(type, elements, data[0] != 0);
	}
}
