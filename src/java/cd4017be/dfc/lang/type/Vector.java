package cd4017be.dfc.lang.type;

import static cd4017be.dfc.lang.Signal.*;
import static java.lang.Integer.highestOneBit;
import static java.lang.System.identityHashCode;

import java.util.Objects;

import cd4017be.dfc.compiler.NodeInstruction;
import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public class Vector implements Type {

	public final Type element;
	public final int count, size, align;
	public final boolean simd, dyn;

	Vector(Type element, int count, boolean simd) {
		this.element = element;
		this.count = count;
		this.simd = simd;
		this.dyn = count == 0 || element.dynamic();
		int size = element.sizeOf() * count;
		if (simd)
			this.size = this.align = highestOneBit(size - 1) << 1;
		else {
			this.size = size;
			this.align = element.align();
		}
	}

	@Override
	public int color(Signal s) {
		return element.color(s) + 16;
	}

	@Override
	public Signal getElement(Signal s, long i) {
		if (i == -1)
			if (simd || s.type != this)
				return var(element);
			else throw new IllegalArgumentException("can't dynamically index array value");
		if (!s.hasValue()) return img(element);
		if (count > 0 || s.type == this)
			Objects.checkIndex((int)i, count);
		if (s.isVar() || s.type != this) return var(element);
		return new Signal(element, CONST, s.getIndex((int)i));
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
		return identityHashCode(element) + count * 31;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(!(obj instanceof Vector)) return false;
		Vector other = (Vector)obj;
		return count == other.count && element == other.element;
	}

	@Override
	public String toString() {
		return (simd ? "<%d x %s>" : "[%d x %s]").formatted(count, element);
	}

	@Override
	public StringBuilder displayString(StringBuilder sb, boolean nest) {
		if (count > 0) sb.append(count).append(' ');
		if (simd) return element.displayString(sb, nest);
		return element.displayString(sb.append('['), nest).append(']');
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
	public boolean canAssignTo(Type t) {
		if (t == this) return true;
		return element.canSimd()
		&& t instanceof Vector
		&& count == ((Vector)t).count
		&& element.canAssignTo(((Vector)t).element);
	}

	@Override
	public void evalConst(NodeInstruction ni, Object val) {
		Type t;
		for (t = element; t instanceof Vector v; t = v.element);
		if (t instanceof Primitive) return;
		if (val instanceof Object[] arr)
			for (Object e : arr)
				element.evalConst(ni, e);
	}

}