package cd4017be.dfc.lang.type;

import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public enum Primitive implements Type {
	UWORD("i8", 8, false, false),
	USHORT("i16", 16, false, false),
	UINT("i32", 32, false, false),
	ULONG("i64", 64, false, false),
	WORD("i8", 8, true, false),
	SHORT("i16", 16, true, false),
	INT("i32", 32, true, false),
	LONG("i64", 64, true, false),
	FLOAT("float", 32, true, true),
	DOUBLE("double", 64, true, true),
	BOOL("i1", 1, false, false),
	LABEL("label", 0, false, false);

	public final String name;
	public final int bits;
	public final boolean signed, fp;

	private Primitive(String name, int bits, boolean signed, boolean fp) {
		this.name = name;
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
	public boolean canSimd() {
		return true;
	}

	@Override
	public boolean canAssignTo(Type t) {
		if (t == this) return true;
		if (!(t instanceof Primitive)) return false;
		Primitive p = (Primitive)t;
		return p.fp || bits < p.bits && (!signed || p.signed);
	}

	@Override
	public boolean canArithmetic() {
		return this != BOOL;
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