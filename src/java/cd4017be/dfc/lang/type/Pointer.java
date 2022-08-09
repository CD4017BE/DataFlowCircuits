package cd4017be.dfc.lang.type;

import static cd4017be.dfc.lang.type.Types.*;
import static java.lang.System.identityHashCode;

import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public class Pointer implements Type {

	/** 0: no_capture, 1: private, 2: shared,
	 *  4: no_capture write_only, 5: write_only, 7: shared write_only,
	 *  8: no_capture read_only, 10: read_only, 11: private read_only,
	 * 12:  */
	public static final int PRIVATE = 1, SHARED = 2, NO_READ = 4, NO_WRITE = 8, NO_CAPTURE = 16;

	public Type type;
	public final int flags;

	public Pointer(int flags) {
		this.flags = flags;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		if (s.type != this)
			throw new IllegalArgumentException("can't sub address into pointer");
		if (type == null) return null;
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
		if (this.type == null) {
			this.type = type;
			return unique(this);
		}
		return new Pointer(flags).to(type);
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
		return type == VOID ? "i8*" : type + "*";
	}

	@Override
	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		return (type == null ? sb.append('?') : type.displayString(sb, nest))
			.append((flags & NO_WRITE) != 0 ? '^' : '*');
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
		return (flags & ~p.flags) == 0
		&& (p.flags & NO_WRITE | (flags | ~p.flags) & SHARED) != 0
		&& (type == p.type || (~p.flags & (NO_WRITE | NO_READ)) == 0);
	}

	public enum Access {
		SRWC(0b1111), PRWC(0b1110), SRW(0b1101), PRW(0b1100),
		SRC(0b1011), PRC(0b1010), SR(0b1001), PR(0b1000),
		SWC(0b0111), PWC(0b0110), SW(0b0101), PW(0b0100),
		SC(0b0011), STACK(0b1100);
		
		public final boolean read, write, capture, share;
		
		private Access(int rwcs) {
			this.read = (rwcs & 0b1000) != 0;
			this.write = (rwcs & 0b0100) != 0;
			this.capture = (rwcs & 0b0010) != 0;
			this.share = (rwcs & 0b0001) != 0;
		}
	}

}