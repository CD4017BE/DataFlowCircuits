package cd4017be.compiler;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author CD4017BE */
public class Type {

	private static final WeakHashMap<Type, WeakReference<Type>> CACHE = new WeakHashMap<>();
	private static final String[] SINGLE = {"el"}, NONE = {};
	private static final Type[] EMPTY = {};

	public static Type builtin(String name) {
		VTable vtable = LoadingCache.CORE.types.get(name);
		if (vtable == null) throw new IllegalStateException("missing builtin type " + name);
		return of(vtable, NONE, EMPTY, 0);
	}

	/**@param vtable behavior of the type
	 * @param n arbitrary number
	 * @return a new or existing Type with no elements */
	public static Type of(VTable vtable, int n) {
		return of(vtable, NONE, EMPTY, n);
	}

	/**@param vtable behavior of the type
	 * @param elem type of the single element
	 * @param n arbitrary number
	 * @return a new or existing Type with just one element type named "el" */
	public static Type of(VTable vtable, Type elem, int n) {
		return of(vtable, SINGLE, new Type[] {elem}, n);
	}

	/**@param vtable behavior of the type
	 * @param names element names
	 * @param elem element types
	 * @param n arbitrary number
	 * @return a new or existing Type for the given arguments */
	public static Type of(VTable vtable, String[] names, Type[] elem, int n) {
		Type ntype = new Type(vtable, names, elem, n);
		WeakReference<Type> ref = CACHE.get(ntype);
		Type type = ref == null ? null : ref.get();
		if (type == null) CACHE.put(type = ntype, new WeakReference<>(ntype));
		return type;
	}


	private final String[] names;
	private final Type[] elem;
	/** the virtual method table used to implement polymorphic operations on this type */
	public final VTable vtable;
	/** arbitrary number (typically used to represent array/vector dimensions) */
	public final int n;
	private final int hash;
	private String name;

	private Type(VTable vtable, String[] names, Type[] elem, int n) {
		if (vtable == null) throw new NullPointerException("vtable must not be null");
		int l = names.length;
		if (l != elem.length) throw new IllegalArgumentException("names and elem must have same length");
		this.names = names;
		this.elem = elem;
		this.vtable = vtable;
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

	@Override
	public String toString() {
		if (name != null) return name;
		String name = vtable.name;
		StringBuilder sb = new StringBuilder();
		for (int i = 0, j = 0, k = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c != '$') sb.append(c);
			else {
				switch(c = name.charAt(++i)) {
				case '$' -> sb.append(c);
				case '[', '(' -> {
					j = i;
					if (k >= elem.length - (c == '(' ? 1 : 0))
						while(++i < name.length()) {
							c = name.charAt(i);
							if (c != '$') continue;
							c = name.charAt(++i);
							if (c == ')' || c == ']') break;
						}
				}
				case ']', ')' -> {
					if (k < elem.length - (c == ')' ? 1 : 0)) i = j;
				}
				case 'n' -> sb.append(n);
				case '+' -> k++;
				case 'x' -> sb.append(names[k]);
				case 'y' -> sb.append(elem[k]);
				}
			}
		}
		return this.name = sb.toString();
	}

}
