package cd4017be.dfc.lang.type;

import cd4017be.dfc.lang.Signal;
import cd4017be.dfc.lang.SignalError;

/**
 * @author CD4017BE */
public interface Type {

	int sizeOf();
	int align();

	default Vector vector(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Vector or Array");
	}

	default Struct struct(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Struct");
	}

	default Function function(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Function");
	}

	default Pointer pointer(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Pointer");
	}

	boolean canSimd();

	int color(Signal s);

	default boolean canAssignTo(Type t) {
		return t == this;
	}

	default boolean canBitcastTo(Type t) {
		return sizeOf() == t.sizeOf();
	}

	Signal getElement(Signal s, long i);

	default boolean canArithmetic() {
		return false;
	}

	default boolean canLogic() {
		return false;
	}

	default boolean canCompare() {
		return false;
	}

}
