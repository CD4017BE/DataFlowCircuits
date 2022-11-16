package cd4017be.compiler;

import java.util.Arrays;
import java.util.HashMap;

/**
 * @author CD4017BE */
public class Type {

	public static final Type VOID = new Type(null, 0);
	private static final String[] SINGLE = {"el"}, NONE = {};
	private static final Type[] EMPTY = {};

	private final String[] names;
	private final Type[] elem;
	/** the virtual method table used to implement polymorphic operations on this type */
	public final HashMap<String, VirtualMethod> vtable;
	/** arbitrary number (typically used to represent array/vector dimensions) */
	public final int n;
	private final int hash;

	/**@param vtableSrc type to share vtable with or null to create new one
	 * @param n arbitrary number */
	public Type(Type vtableSrc, int n) {
		this(vtableSrc, NONE, EMPTY, n);
	}

	/**@param vtableSrc type to share vtable with or null to create new one
	 * @param elem type of the single element
	 * @param n arbitrary number */
	public Type(Type vtableSrc, Type elem, int n) {
		this(vtableSrc, SINGLE, new Type[] {elem}, n);
	}

	/**@param vtableSrc type to share vtable with or null to create new one
	 * @param names element names
	 * @param elem element types
	 * @param n arbitrary number */
	public Type(Type vtableSrc, String[] names, Type[] elem, int n) {
		int l = names.length;
		if (l != elem.length) throw new IllegalArgumentException();
		this.names = names;
		this.elem = elem;
		this.vtable = vtableSrc != null ? vtableSrc.vtable : new HashMap<>();
		this.n = n;
		int hash = 31 * n + System.identityHashCode(vtable);
		for (int i = 0; i < l; i++) {
			hash = 31 * hash + names[i].hashCode();
			hash = 31 * hash + elem[i].hashCode();
		}
		this.hash = hash;
	}

	public int index(String name) {
		for (int i = 0; i < names.length; i++)
			if (name.equals(names[i]))
				return i;
		return -1;
	}

	public int count() {
		return elem.length;
	}

	public String name(int i) {
		return names[i];
	}

	public Type elem(int i) {
		return elem[i];
	}

	public Type unique(HashMap<Type, Type> set) {
		Type type = set.putIfAbsent(this, this);
		return type == null ? this : type;
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) return true;
		return obj instanceof Type other
		&& hash == other.hash
		&& n == other.n
		&& vtable == other.vtable
		&& Arrays.equals(elem, other.elem)
		&& Arrays.equals(names, other.names);
	}

}
