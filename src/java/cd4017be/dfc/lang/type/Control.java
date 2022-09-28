package cd4017be.dfc.lang.type;

import cd4017be.dfc.lang.Signal;

/**
 * 
 * @author CD4017BE */
public class Control implements Type {

	public static final Signal UNREACHABLE = new Signal(new Control(null, null), Signal.VAR, null);

	final Signal cont, brk;

	public static Signal control(Signal cont, Signal brk) {
		if (cont == UNREACHABLE) cont = null;
		if (brk == UNREACHABLE) brk = null;
		if (cont == null && brk == null) return UNREACHABLE;
		return Signal.var(new Control(cont, brk));
	}

	public Control(Signal cont, Signal brk) {
		this.cont = cont;
		this.brk = brk;
	}

	@Override
	public int sizeOf() {
		return 0;
	}

	@Override
	public int align() {
		return 0;
	}

	@Override
	public boolean canSimd() {
		return false;
	}

	@Override
	public int color(Signal s) {
		return s.hasValue() ? 31 : 63;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		throw new IllegalArgumentException("can't index control flow");
	}

	@Override
	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		if (cont == null && brk == null)
			return sb.append("unreachable");
		if (brk != null)
			sb = brk.displayString(sb.append("break("), false).append(')');
		if (cont != null)
			sb = cont.displayString(sb.append("continue("), false).append(')');
		return sb;
	}

	public Signal cont() {
		return cont == null ? UNREACHABLE : cont;
	}

	public Signal brk() {
		return brk == null ? UNREACHABLE : brk;
	}

}
