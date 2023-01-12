package cd4017be.compiler;

import java.util.Collection;

/**
 * 
 * @author CD4017BE */
public abstract class Scope {

	public final Scope parent;
	public final int lvl;

	protected Scope(Scope parent, int lvl) {
		this.parent = parent;
		this.lvl = lvl;
	}

	public abstract void getSrc(Collection<ScopeBranch> src);
	public abstract boolean isIn(Collection<ScopeBranch> src, int lvl);
	public abstract void addMember(Node node);

	protected void buildRelPath(StringBuilder sb, Scope parent) {
		if (this.parent != parent) {
			this.parent.buildRelPath(sb, parent);
			sb.append(':');
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		buildRelPath(sb, null);
		return sb.toString();
	}

	public static void main(String[] args) {
//		Scope
//		a = new ScopeBranch(ROOT, 1, 0),
//		b = new ScopeBranch(ROOT, 1, 1),
//		c = new ScopeBranch(ROOT, 1, -2),
//		u = union(a, b), v = union(b, c),
//		d = new ScopeBranch(u, 2, 0),
//		e = new ScopeBranch(u, 2, -1);
//		System.out.println(union(v, d, e));
	}

}
