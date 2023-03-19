package cd4017be.dfc.lang;

import java.util.ArrayList;
import java.util.Collection;
import cd4017be.dfc.lang.Node.Vertex;
import cd4017be.dfc.lang.instructions.LoopIns;
import cd4017be.dfc.lang.instructions.SwitchIns;

/**
 * 
 * @author CD4017BE */
public class ScopeBranch extends Scope {

	public final Node node;
	public final int path;
	public final ArrayList<Node> members = new ArrayList<>();
	private Instruction[] code;
	public int addr;

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

	public Instruction[] compile(int[] errLut) throws SignalError {
		if (this.code != null) return code;
		Instruction[] code = this.code = new Instruction[members.size()];
		for (int i = 0, ri = code.length - 1; ri >= 0; i++, ri--) {
			Node node = members.get(i);
			errLut[node.addr] = node.idx;
			switch(node.mode) {
				case Node.SWT -> {
					Instruction[][] branches = new Instruction[node.in.length - 1][];
					int[] io = new int[branches.length + 3];
					io[0] = node.addr(this.addr);
					io[1] = this.addr;
					io[2] = node.in[0].addr(this.addr);
					for (int j = 0; j < branches.length; j++) {
						Vertex in = node.in[j + 1];
						io[j + 3] = in.addr(this.addr);
						branches[j] = ((ScopeBranch)in.scope()).compile(errLut);
					}
					code[ri] = new SwitchIns(branches, io);
				}
				case Node.END -> {
					Vertex in = node.in[2];
					ScopeBranch scope = (ScopeBranch)in.scope();
					code[ri] = new LoopIns(scope.compile(errLut), node.in[1].addr(this.addr), in.addr(this.addr), node.in[0].addr(this.addr), this.addr, scope.addr);
				}
				default -> {
					int[] io = new int[node.in.length + 2];
					io[0] = node.addr(this.addr);
					io[1] = this.addr;
					for (int j = 0; j < node.in.length; j++)
						io[j + 2] = node.in[j].addr(this.addr);
					code[ri] = node.op.setIO(io);
				}
			}
		}
		return code;
	}

}
