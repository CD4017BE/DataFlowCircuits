package cd4017be.dfc.lang.type;

import cd4017be.dfc.lang.Signal;
import cd4017be.dfc.lang.SignalError;

/**
 * @author CD4017BE */
public class Bundle implements Type {

	public final Signal[] source;

	public Bundle(Signal... source) {
		this.source = source;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		if (i == -1)
			throw new IllegalArgumentException("can't dynamically index bundle");
		return s.getElement((int)i);
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
	public Vector vector(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Vector, got Bundle");
	}

	@Override
	public Struct struct(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Struct, got Bundle");
	}

	@Override
	public Function function(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Function, got Bundle");
	}

	@Override
	public Pointer pointer(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Pointer, got Bundle");
	}

	@Override
	public boolean canSimd() {
		return false;
	}

}
