package cd4017be.dfc.lang.type;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public class Struct implements Type {

	public final Type[] elements;
	private final String[] names;
	private final int[] nameIdx;
	public final int id, size, align;

	Struct(Type[] elements, String[] names, int id) {
		this.elements = elements;
		this.names = names;
		this.nameIdx = Types.nameIndex(names);
		this.id = id;
		int size = 0, align = 0;
		for (Type t : elements) {
			align = Math.max(align, t.align());
			size = (size + align - 1 & -align) + t.sizeOf();
		}
		this.size = size;
		this.align = align;
	}

	@Override
	public int getIndex(String name) {
		if (names == null) return -1;
		int i = Arrays.binarySearch(names, name);
		return i < 0 ? -1 : nameIdx[i];
	}

	@Override
	public Signal getElement(Signal s, long i) {
		if (i == -1L) throw new IllegalArgumentException("can't dynamically index struct");
		return new Signal(elements[(int)i], s.state, s.isConst() ? s.getIndex((int)i) : 0L);
	}

	@Override
	public int color(Signal s) {
		return s.hasValue() ? 14 : 46;
	}

	@Override
	public int sizeOf() {
		return size;
	}

	@Override
	public int align() {
		return align;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(names) * 31
		+ Types.contentIdentityHash(elements);
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof Struct)) return false;
		Struct other = (Struct)obj;
		return Types.contentIdentical(elements, other.elements)
		&& Arrays.equals(names, other.names);
	}

	@Override
	public String toString() {
		return id >= 0 ? "%" + id
			: elements.length == 0 ? "{}"
			: elements[0].toString();
	}

	void define(Writer w) throws IOException {
		w.append('%').append(Integer.toString(id)).append(" = type {");
		w.append(elements[0].toString());
		for (int i = 1; i < elements.length; i++)
			w.append(", ").append(elements[i].toString());
		w.append("}\n");
	}

	public boolean isWrapper() {
		return id < 0;
	}

	public Type unwrap() {
		return elements[0];
	}

	@Override
	public boolean canSimd() {
		return false;
	}

	@Override
	public boolean canAssignTo(Type t) {
		return this == t || isWrapper() && unwrap().canAssignTo(t);
	}

}