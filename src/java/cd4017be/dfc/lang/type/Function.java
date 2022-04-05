package cd4017be.dfc.lang.type;

import static cd4017be.dfc.lang.Signal.*;
import static cd4017be.dfc.lang.type.Types.*;
import static java.lang.System.identityHashCode;

import java.util.Arrays;

import cd4017be.dfc.lang.Signal;
import cd4017be.dfc.lang.SignalError;

/**
 * @author CD4017BE */
public class Function implements Type {

	public final Type retType;
	public final Type[] parTypes;

	Function(Type retType, Type[] parTypes) {
		this.retType = retType;
		this.parTypes = parTypes;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		if (i == -1)
			throw new IllegalArgumentException("can't dynamically index function parameters");
		if (s.type != this)
			throw new IllegalArgumentException("can't sub address into function pointer");
		if (i == 0) return img(retType);
		return new Signal(parTypes[(int)i - 1], s.isConst() ? VAR : IMAGE, 0L);
	}

	@Override
	public int color(Signal s) {
		return s.hasValue() ? 12 : 44;
	}

	@Override
	public int sizeOf() {
		return FUN_SIZE;
	}

	@Override
	public int align() {
		return FUN_SIZE;
	}

	@Override
	public int hashCode() {
		return contentIdentityHash(parTypes) * 31 + identityHashCode(retType);
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof Function)) return false;
		Function other = (Function)obj;
		return retType == other.retType && contentIdentical(parTypes, other.parTypes);
	}

	@Override
	public String toString() {
		String par = Arrays.toString(parTypes);
		return "%s(%s)*".formatted(
			retType == VOID ? "void" : retType,
			par.subSequence(1, par.length() - 1)
		);
	}

	@Override
	public boolean canSimd() {
		return false;
	}

	@Override
	public Vector vector(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Vector, got Function");
	}

	@Override
	public Struct struct(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Bundle, got Function");
	}

	@Override
	public Function function(int node, int in) {
		return this;
	}

	@Override
	public Pointer pointer(int node, int in) throws SignalError {
		throw new SignalError(node, in, "expected Pointer, got Function");
	}

	@Override
	public boolean canCompare() {
		return true;
	}

}