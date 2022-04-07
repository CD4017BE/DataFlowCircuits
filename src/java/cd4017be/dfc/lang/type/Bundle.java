package cd4017be.dfc.lang.type;

import java.util.Objects;

import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public class Bundle implements Type {

	public Bundle parent;
	public final Signal signal;
	public final String name;

	public Bundle(Bundle parent, Signal signal, String name) {
		this.parent = parent;
		this.signal = signal;
		this.name = name;
	}

	@Override
	public int getIndex(String name) {
		for(Bundle b = this; b != null; b = b.parent)
			if (name.equals(b.name)) {
				int i = 0;
				while((b = b.parent) != null) i++;
				return i;
			}
		return -1;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		if (i == -1)
			throw new IllegalArgumentException("can't dynamically index bundle");
		Bundle b = this;
		Objects.checkIndex((int)i, (int)s.value);
		for (int j = (int)(s.value - i) - 1; j > 0; j--)
			b = b.parent;
		return b.signal;
	}

	@Override
	public int color(Signal s) {
		return s.hasValue() ? 31 : 63;
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
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Bundle b = this; b != null; b = b.parent)
			sb.append(b.signal.type).append(' ').append(b.signal).append(", ");
		return sb.delete(sb.length() - 2, sb.length()).toString();
	}

	public Signal toSignal(int l) {
		return new Signal(this, Signal.VAR, l);
	}

}
