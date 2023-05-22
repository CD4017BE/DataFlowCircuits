package cd4017be.dfc.lang;

/**
 * @author cd4017be */
public class Type {

	public final String id;
	public final Module module;
	public final int color0, color1;

	public Type(Module module, String id, int color0, int color1) {
		this.module = module;
		this.id = id;
		this.color0 = color0;
		this.color1 = color1;
		module.types.put(id, this);
	}

	@Deprecated
	public Instruction lookup(String name) {
		//TODO reimplement
		return null;
	}

	public int color(Value val) {
		return module.trace0 + (val.elements.length == 0 && val.data.length == 0 ? color0 : color1);
	}

	@Override
	public String toString() {
		return module + ":" + id;
	}

}
