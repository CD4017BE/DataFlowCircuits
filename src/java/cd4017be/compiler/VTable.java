package cd4017be.compiler;

import java.util.HashMap;


/**
 * 
 * @author CD4017BE */
public class VTable extends HashMap<String, VirtualMethod> {
	private static final long serialVersionUID = -2902223347716374669L;

	public final Module module;
	public final String id, name;
	public final int color;

	public VTable(Module module, String id, String name, int color) {
		this.module = module;
		this.id = id;
		this.name = name;
		this.color = color;
	}

	@Override
	public boolean equals(Object o) {
		return o == this || o instanceof VTable other
		&& other.module == module && other.id.equals(id);
	}

	@Override
	public int hashCode() {
		return module.hashCode() * 31 + id.hashCode();
	}

	@Override
	public String toString() {
		return module + ":" + id;
	}

}
