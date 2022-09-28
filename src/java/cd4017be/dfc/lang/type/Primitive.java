package cd4017be.dfc.lang.type;

import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public enum Primitive implements Type {
	UWORD("i8", "C", 8, false, false),
	USHORT("i16", "S", 16, false, false),
	UINT("i32", "I", 32, false, false),
	ULONG("i64", "L", 64, false, false),
	WORD("i8", "c", 8, true, false),
	SHORT("i16", "s", 16, true, false),
	INT("i32", "i", 32, true, false),
	LONG("i64", "l", 64, true, false),
	FLOAT("float", "f", 32, true, true),
	DOUBLE("double", "d", 64, true, true),
	BOOL("i1", "b", 1, false, false),
	LABEL("label", "!", 0, false, false);

	public final String name, dspName;
	public final int bits;
	public final boolean signed, fp;

	private Primitive(String name, String dspName, int bits, boolean signed, boolean fp) {
		this.name = name;
		this.dspName = dspName;
		this.bits = bits;
		this.signed = signed;
		this.fp = fp;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		throw new IllegalArgumentException("can't index into primitive");
	}

	@Override
	public int color(Signal s) {
		return s.hasValue() ? ordinal() + 1 : ordinal() + 33;
	}

	@Override
	public int sizeOf() {
		return bits + 7 >> 3;
	}

	@Override
	public int align() {
		return bits + 7 >> 3;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		return sb.append(dspName);
	}

	@Override
	public boolean canSimd() {
		return true;
	}

	@Override
	public boolean canAssignTo(Type t, boolean cast) {
		if (t == this) return true;
		if (t instanceof Primitive p)
			return cast || p.fp || bits <= p.bits && (!signed || p.signed);
		if (t instanceof Pointer p)
			return (~p.flags & (Pointer.NO_WRITE | Pointer.NO_READ)) == 0;
		return false;
	}

	@Override
	public boolean isInt() {
		return bits > 1 && !fp;
	}

	@Override
	public boolean canArithmetic() {
		return bits > 1;
	}

	@Override
	public boolean canLogic() {
		return !fp;
	}

	@Override
	public boolean canCompare() {
		return this != BOOL;
	}

}