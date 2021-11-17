package cd4017be.dfc.lang;

import java.util.ArrayList;

/**
 * @author CD4017BE */
public class GlobalVar {

	public static final ArrayList<GlobalVar> GLOBALS = new ArrayList<>();
	private static int NEXT_IDX;

	public static void clear() {
		NEXT_IDX = 0;
		GLOBALS.clear();
	}

	public final Node node;
	public final String name;
	public String type;
	public int len;

	/**@param node that defines the global
	 * @param name optional name */
	public GlobalVar(Node node, String name) {
		this.node = node;
		this.name = "@" + (name != null ? name : NEXT_IDX++);
		GLOBALS.add(this);
	}

	@Override
	public String toString() {
		return name;
	}

}
