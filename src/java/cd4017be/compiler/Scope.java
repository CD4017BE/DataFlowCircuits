package cd4017be.compiler;

import java.util.ArrayList;

/**
 * TODO add some data storage fields
 * @author CD4017BE */
public class Scope {
	/** The root scope which has no parent and is the ultimate parent of all other scopes. */
	public static final Scope ROOT = new Scope();

	public final Scope parent;
	/** nesting level >= 0 */
	public final int lvl;
	ArrayList<Signal> signals = new ArrayList<>();

	private Scope() {
		this.parent = null;
		this.lvl = 0;
	}

	/**@param parent of the new Scope */
	public Scope(Scope parent) {
		this.parent = parent;
		this.lvl = parent.lvl + 1;
	}


	/**@param a
	 * @param b
	 * @return the first common parent scope of a and b or null if both arguments null */
	public static Scope union(Scope a, Scope b) {
		if (a == null) return b;
		if (b == null) return a;
		while(a.lvl > b.lvl) a = a.parent;
		while(b.lvl > a.lvl) b = b.parent;
		while (a != b) {
			a = a.parent;
			b = b.parent;
		}
		return a;
	}

}