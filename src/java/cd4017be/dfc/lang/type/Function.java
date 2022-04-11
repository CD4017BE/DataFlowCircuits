package cd4017be.dfc.lang.type;

import static cd4017be.dfc.lang.Signal.*;
import static cd4017be.dfc.lang.type.Types.*;
import static java.lang.System.identityHashCode;

import java.util.Arrays;

import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public class Function implements Type {

	public static Signal CUR_FUNCTION;

	public final Type retType;
	public final Type[] parTypes;
	private final String[] parNames;
	private final int[] nameIdx;

	Function(Type retType, Type[] parTypes, String[] parNames) {
		this.retType = retType;
		this.parTypes = parTypes;
		this.parNames = parNames;
		this.nameIdx = Types.nameIndex(parNames);
	}

	@Override
	public int getIndex(String name) {
		if (parNames == null) return -2;
		int i = Arrays.binarySearch(parNames, name);
		return i < 0 ? -2 : nameIdx[i] + 1;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		if (i == -1)
			throw new IllegalArgumentException("can't dynamically index function parameters");
		if (s.type != this)
			throw new IllegalArgumentException("can't sub address into function pointer");
		if (i == 0) return img(retType);
		Type t = parTypes[(int)i - 1];
		if (s != CUR_FUNCTION) return img(t);
		return new Signal(t, VAR, i - 1);
	}

	@Override
	public int color(Signal s) {
		return s.hasValue() ? 13 : 45;
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
		return (Arrays.hashCode(parNames) * 31
		+ contentIdentityHash(parTypes)) * 31
		+ identityHashCode(retType);
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof Function)) return false;
		Function other = (Function)obj;
		return retType == other.retType
		&& contentIdentical(parTypes, other.parTypes)
		&& Arrays.equals(parNames, other.parNames);
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
	public boolean canCompare() {
		return true;
	}

	@Override
	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		retType.displayString(sb, false).append('(');
		for (int i = 0, l = parTypes.length; i < l; i++) {
			if (i > 0) sb.append(", ");
			parTypes[i].displayString(sb, false)
			.append(' ').append(parNames[nameIdx[i + l]]);
		}
		return sb.append(')');
	}

}