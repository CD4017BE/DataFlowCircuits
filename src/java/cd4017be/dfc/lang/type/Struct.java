package cd4017be.dfc.lang.type;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import cd4017be.dfc.compiler.NodeInstruction;
import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public class Struct implements Type {

	public final Type[] elements;
	protected final String[] names;
	private final int[] nameIdx;
	public final int id, size, align;
	public final boolean dyn;

	Struct(Type[] elements, String[] names, int id) {
		this.elements = elements;
		this.names = names;
		this.nameIdx = Types.nameIndex(names);
		this.id = id;
		int size = 0, align = 1;
		boolean dyn = false;
		for (Type t : elements) {
			align = Math.max(align, t.align());
			size = (size + align - 1 & -align) + t.sizeOf();
			dyn |= t.dynamic();
		}
		this.size = size;
		this.align = align;
		this.dyn = dyn;
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
		return id >= 0 ? "%" + id : "void";
	}

	@Override
	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		if (elements.length == 0)
			return sb.append(names.length == 0 ? "{}" : names[0]);
		if (!nest) return sb.append("T").append(id);
		sb.append('{');
		for (int i = 0, l = elements.length; i < l; i++) {
			if (i > 0) sb.append(", ");
			elements[i].displayString(sb, false)
			.append(' ').append(names[nameIdx[i + l]]);
		}
		return sb.append('}');
	}

	void define(Writer w) throws IOException {
		w.append('%').append(Integer.toString(id));
		if (elements.length > 0) {
			w.append(" = type {").append(elements[0].toString());
			for (int i = 1; i < elements.length; i++)
				w.append(", ").append(elements[i].toString());
			w.append("}\n");
		} else w.append(" = type opaque\n");
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
	public boolean dynamic() {
		return dyn;
	}

	@Override
	public void evalConst(NodeInstruction ni, Object val) {
		if (val instanceof Object[] arr)
			for (int i = 0; i < elements.length; i++)
				elements[i].evalConst(ni, arr[i]);
	}

}