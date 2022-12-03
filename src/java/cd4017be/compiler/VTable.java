package cd4017be.compiler;

import java.util.HashMap;


/**
 * 
 * @author CD4017BE */
public class VTable extends HashMap<String, VirtualMethod> {
	private static final long serialVersionUID = -2902223347716374669L;

	public final String name;
	public final int color;

	public VTable(String name, int color) {
		this.name = name;
		this.color = color;
	}

}
