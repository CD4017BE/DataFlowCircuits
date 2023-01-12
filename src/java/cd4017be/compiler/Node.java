package cd4017be.compiler;

import java.util.ArrayList;

/**
 * 
 * @author CD4017BE */
public class Node {

	public static final int INSTR = 0, SWT = 1, BEGIN = 2, END = 3, OUT = 4, IN = 5;

	public final Instruction op;
	public final Vertex[] in;
	private Vertex out;
	public final int mode;
	private int wait;
	int addr;

	public Node(Node out) {
		this();
		in[0].connect(out);
	}

	public Node() {
		this(null, OUT, 1);
	}

	public Node(int in) {
		this(null, IN, 0);
		addr = in + 1;
	}

	public Node(Instruction op, int mode, Node... ins) {
		this(op, mode, ins.length);
		for(int i = 0; i < ins.length; i++)
			in[i].connect(ins[i]);
	}

	public Node(Instruction op, int mode, int ins) {
		if (ins < switch(mode) {
			case INSTR, END, IN -> 0;
			case BEGIN, OUT -> 1;
			case SWT -> 3;
			default -> throw new IllegalArgumentException("invalid mode");
		}) throw new IllegalArgumentException("too few inputs for mode");
		this.op = op;
		this.mode = mode;
		this.in = new Vertex[ins];
		for (int i = 0; i < ins; i++)
			in[i] = new Vertex(this, i);
	}

	public int computeScope(int nextAddr) {
		int l = 0;
		for (Vertex v = out; v != null; v = v.next) l++;
		Scope[] src = new Scope[l]; l = 0;
		for (Vertex v = out; v != null; v = v.next)
			src[l++] = v.scope;
		Scope scope = ScopeUnion.union(src);
		switch(mode) {
			case INSTR -> {
				addr = nextAddr++;
				scope.addMember(this);
				for (Vertex v : in) v.scope = scope;
			}
			case SWT -> {
				addr = nextAddr++;
				scope.addMember(this);
				in[0].scope = scope;
				in[1].scope = new ScopeBranch(scope, this, 2 - in.length).addr(nextAddr++);
				for (int i = 2; i < in.length; i++)
					in[i].scope = new ScopeBranch(scope, this, i - 2).addr(nextAddr++);
			}
			case END -> {
				addr = nextAddr++;
				scope.addMember(this);
				scope = new ScopeBranch(scope, this, 0).addr(nextAddr++);
				for (Vertex v : in) v.scope = scope;
			}
			case BEGIN -> {
				while (!(scope instanceof ScopeBranch sb && sb.node.mode == END))
					scope = scope.parent;
				addr = nextAddr++;
				scope.addMember(this);
				in[0].scope = scope.parent;
			}
			case OUT -> {
				in[0].scope = new ScopeBranch(null, this, 0).addr(0);
			}
		}
		return nextAddr;
	}

	public static int evalScopes(Node root, int nextAddr) {
		ArrayList<Node> stack = new ArrayList<>();
		stack.add(root);
		while(!stack.isEmpty()) {
			Node node = stack.remove(stack.size() - 1);
			nextAddr = node.computeScope(nextAddr);
			for (Vertex in : node.in) {
				Node from = in.from;
				if (--from.wait == 0)
					stack.add(from);
			}
		}
		return nextAddr;
	}

	

	@Override
	public String toString() {
		return "#" + addr;
	}


	public static class Vertex {
		public final Node to;
		public final int toIdx;
		Node from;
		Vertex next;
		Scope scope;

		Vertex(Node node, int idx) {
			this.to = node;
			this.toIdx = idx;
		}

		public void connect(Node node) {
			if (from != null) {
				Vertex v = from.out;
				if (v == this) from.out = next;
				else for (Vertex u; v != null; v = u)
					if ((u = v.next) == this) {
						v.next = next;
						break;
					}
				if (scope == null) from.wait--;
			}
			if (node != null) {
				next = node.out;
				node.out = this;
				if (scope == null) node.wait++;
			} else next = null;
			from = node;
		}

		public int addr() {
			return from == null ? -1 : from.addr;
		}

		public Scope scope() {
			return scope;
		}

	}

}
