package cd4017be.compiler;

import java.util.ArrayList;
import java.util.Collection;

/**
 * 
 * @author CD4017BE */
public class ScopeBranch extends Scope {

	public final Node node;
	public final int path;
	public final ArrayList<Node> members = new ArrayList<>();
	int addr;

	public ScopeBranch(Scope parent, Node node, int path) {
		super(parent, parent == null ? 0 : parent.lvl + 1);
		this.node = node;
		this.path = path;
	}

	public ScopeBranch addr(int i) {
		this.addr = i;
		return this;
	}

	@Override
	public void getSrc(Collection<ScopeBranch> src) {
		if (!src.contains(this)) src.add(this);
	}

	@Override
	public boolean isIn(Collection<ScopeBranch> src, int lvl) {
		return src.contains(this) || this.lvl > lvl && parent.isIn(src, lvl);
	}

	@Override
	protected void buildRelPath(StringBuilder sb, Scope parent) {
		super.buildRelPath(sb, parent);
		sb.append(node).append('_').append(path);
	}

	@Override
	public void addMember(Node node) {
		members.add(node);
	}

	public CodeBlock compile(CodeBlock skip, CodeBlock next, int loop) {
		CodeBlock block, first;
		if (loop >= 0) {
			block = first = new CodeBlock(2).swt(addr, loop, skip);
			first.br(0, next, -1);
		} else block = first = new CodeBlock(0).next(addr, skip, next);
		int i0 = 0;
		for (int i1 = i0; i1 < members.size(); i1++) {
			Node node = members.get(i1);
			switch(node.mode) {
				case Node.SWT -> {
					block.ops(members.subList(i0, i0 = i1 + 1));
					CodeBlock nxt = block;
					CodeBlock prev = new CodeBlock(node.in.length - 1).swt(addr, node.in[0].addr(), skip);
					for (int i = node.in.length - 1; i > 0; i--) {
						ScopeBranch br = (ScopeBranch)node.in[i].scope;
						nxt = br.compile(block, nxt, -1);
						prev.br(i - 1, nxt, br.addr);
					}
					block = prev;
				}
				case Node.END -> {
					block.ops(members.subList(i0, i0 = i1 + 1));
					ScopeBranch br = (ScopeBranch)node.in[0].scope;
					Node begin = null;
					for (int i = br.members.size() - 1; i >= 0; i--) {
						Node n = br.members.get(i);
						if (n.mode == Node.BEGIN) {
							begin = n;
							break;
						}
					}
					CodeBlock prev = new CodeBlock(1).swt(addr, begin.in[0].addr(), skip);
					prev.br(0, br.compile(block, block, node.in[0].addr()), br.addr);
					block = prev;
				}
			}
		}
		block.ops(members.subList(i0, members.size()));
		if (loop >= 0) first.br(1, block, addr);
		return block;
	}

}
