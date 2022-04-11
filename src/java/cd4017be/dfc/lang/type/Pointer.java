package cd4017be.dfc.lang.type;

import static cd4017be.dfc.lang.type.Types.*;
import static java.lang.System.identityHashCode;

import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public class Pointer implements Type {

	public static final int NO_CAPTURE = 1, READ_ONLY = 2;

	public Type type;
	public final int flags;

	public Pointer(int flags) {
		this.flags = flags;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		if (s.type != this)
			throw new IllegalArgumentException("can't sub address into pointer");
		if (s.hasValue())
			return Signal.var(new Pointer(flags).to(type.getElement(s, i).type));
		return Signal.img(type);
	}

	@Override
	public int color(Signal s) {
		return s.hasValue() ? 12 : 44;
	}

	@Override
	public int sizeOf() {
		return PTR_SIZE;
	}

	@Override
	public int align() {
		return PTR_SIZE;
	}

	public Pointer to(Type type) {
		this.type = type;
		return unique(this);
	}

	@Override
	public int hashCode() {
		return identityHashCode(type) * 31 + flags;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof Pointer)) return false;
		Pointer other = (Pointer)obj;
		return flags == other.flags && type == other.type;
	}

	@Override
	public String toString() {
		return type == VOID ? "ptr" : type + "*";
	}

	@Override
	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		return type.displayString(sb, nest).append((flags & READ_ONLY) != 0 ? '^' : '*');
	}

	@Override
	public boolean canSimd() {
		return true;
	}

	@Override
	public boolean canCompare() {
		return true;
	}

	@Override
	public boolean canAssignTo(Type t) {
		if (t == this) return true;
		if (!(t instanceof Pointer)) return false;
		Pointer p = (Pointer)t;
		return (p.type == type || p.type == VOID) && (flags & ~p.flags) == 0;
	}

}