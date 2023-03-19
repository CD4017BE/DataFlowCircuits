package cd4017be.dfc.lang;

import static java.lang.Math.min;

import java.util.*;

/**
 * 
 * @author CD4017BE */
public class ScopeUnion extends Scope {

	public static final ScopeUnion UNUSED = new ScopeUnion();

	final ScopeBranch[] src;

	private ScopeUnion() {
		super(null, 0x40000000);
		this.src = new ScopeBranch[0];
	}

	private ScopeUnion(ScopeBranch[] src) {
		super(parent(src), lvl(src));
		this.src = src;
	}

	private static Scope parent(ScopeBranch[] src) {
		Scope parent = src[0];
		for (int i = 1; i < src.length; i++)
			for (Scope other = src[i];;) {
				while(other.lvl != parent.lvl)
					if (other.lvl > parent.lvl)
						other = other.parent;
					else parent = parent.parent;
				if (other == parent) break;
				other = other.parent;
				parent = parent.parent;
			}
		return parent;
	}

	private static int lvl(ScopeBranch[] src) {
		int lvl = src[0].lvl;
		for (int i = 1; i < src.length; i++)
			lvl = min(lvl, src[i].lvl);
		return lvl;
	}

	@Override
	public void getSrc(Collection<ScopeBranch> src) {
		for (ScopeBranch sb : this.src)
			if (!src.contains(sb)) src.add(sb);
	}

	@Override
	public boolean isIn(Collection<ScopeBranch> src, int lvl) {
		if (parent.lvl >= lvl && parent.isIn(src, lvl))
			return true;
		for (ScopeBranch sb : this.src)
			if (!sb.isIn(src, lvl))
				return false;
		return true;
	}

	@Override
	protected void buildRelPath(StringBuilder sb, Scope parent) {
		super.buildRelPath(sb, parent);
		sb.append('(');
		for (int i = 0; i < src.length; i++) {
			if (i > 0) sb.append('|');
			src[i].buildRelPath(sb, this.parent);
		}
		sb.append(')');
	}

	public static Scope union(Scope... uses) {
		if (uses.length == 0) return UNUSED;
		//eliminate duplicate scopes
		int n = 1;
		uses: for (int i = 1; i < uses.length; i++) {
			Scope s = uses[i];
			for (int j = 0; j < n; j++)
				if (uses[j] == s)
					continue uses;
			uses[n++] = s;
		}
		if (n == 1) return uses[0];
		//collect unique branches
		ArrayList<ScopeBranch> src = new ArrayList<>();
		for (int i = 0; i < n; i++)
			uses[i].getSrc(src);
		if (src.isEmpty()) return UNUSED;
		if (src.size() == 1) return src.get(0);
		//merge switches
		merge: for (int i = 0; i < src.size(); i++) {
			ScopeBranch sb = src.get(i);
			if (sb.path >= 0) continue;
			//found an "else" case, now find the other cases of that switch
			int m = 1 - sb.path;
			int[] idx = new int[m];
			for (int j = 0; j < src.size(); j++) {
				if (src.get(j).node != sb.node) continue;
				idx[--m] = j;
				if (m == 0) {
					//all cases found, now replace them with parent
					for (int k : idx) {
						ScopeBranch tmp = src.remove(src.size() - 1);
						if (k < src.size()) src.set(k, tmp);
					}
					sb.parent.getSrc(src);
					//restart search
					if (src.isEmpty()) return UNUSED;
					if (src.size() == 1) return src.get(0);
					i = -1;
					continue merge;
				}
			}
		}
		//eliminate redundant child branches
		src.sort((o1, o2) -> o1.lvl - o2.lvl);
		int lvl = src.get(0).lvl; n = 1;
		for (int i = 1; i < src.size(); i++) {
			ScopeBranch scope = src.get(i);
			if (!scope.isIn(src.subList(0, n), lvl))
				src.set(n++, scope);
		}
		if (n == 1) return src.get(0);
		//wrap result
		return new ScopeUnion(src.subList(0, n).toArray(ScopeBranch[]::new));
	}

	@Override
	public void addMember(Node node) {
		for (ScopeBranch sb : src)
			sb.addMember(node);
	}

}
