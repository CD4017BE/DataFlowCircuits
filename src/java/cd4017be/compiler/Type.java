package cd4017be.compiler;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author CD4017BE */
public class Type {

	private static final WeakHashMap<Type, WeakReference<Type>> CACHE = new WeakHashMap<>();

	private static final String[] SINGLE = {"el"}, NONE = {};
	private static final Type[] EMPTY = {};
	//public static final Type VOID = new Type(new VTable(null, "void", "void", 2), 0);

	private final String[] names;
	private final Type[] elem;
	/** the virtual method table used to implement polymorphic operations on this type */
	public final VTable vtable;
	/** arbitrary number (typically used to represent array/vector dimensions) */
	public final int n;
	private final int hash;
	private String name;

	/**@param vtable behavior of the new type
	 * @param n arbitrary number */
	public Type(VTable vtable, int n) {
		this(vtable, NONE, EMPTY, n);
	}

	/**@param vtable behavior of the new type
	 * @param elem type of the single element
	 * @param n arbitrary number */
	public Type(VTable vtable, Type elem, int n) {
		this(vtable, SINGLE, new Type[] {elem}, n);
	}

	/**@param vtable behavior of the new type
	 * @param names element names
	 * @param elem element types
	 * @param n arbitrary number */
	public Type(VTable vtable, String[] names, Type[] elem, int n) {
		int l = names.length;
		if (l != elem.length) throw new IllegalArgumentException();
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

	public Type unique() {
		WeakReference<Type> ref = CACHE.get(this);
		Type type = ref == null ? null : ref.get();
		if (type != null) return type;
		CACHE.put(this, new WeakReference<>(this));
		return this;
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
