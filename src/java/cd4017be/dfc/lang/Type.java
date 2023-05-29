package cd4017be.dfc.lang;

import modules.dfc.module.Intrinsics;

/**
 * @author cd4017be */
public class Type {

	public final String id;
	public final Module module;
	public int color0, color1;
	private boolean defined;

	Type(Module module, String id) {
		this.module = module;
		this.id = id;
	}

	public Type define(int color0, int color1) {
		this.color0 = color0;
		this.color1 = color1;
		defined = true;
		return this;
	}

	@Deprecated
	public Instruction lookup(String name) {
		//TODO reimplement
		return null;
	}

	public int color(Value val) {
		if (!defined) Intrinsics.loadType(this);
		return module.trace0 + (val.elements.length == 0 && val.data.length == 0 ? color0 : color1);
	}

	@Override
	public String toString() {
		return module + ":" + id;
	}

}
