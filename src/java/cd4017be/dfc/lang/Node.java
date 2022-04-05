package cd4017be.dfc.lang;

import java.lang.invoke.MethodHandle;

import cd4017be.dfc.compiler.Instruction;

/**
 * @author CD4017BE */
public class Node {

	public MethodHandle com;
	public Node[] in;
	public Signal out;
	public Object data;
	public int used;
	public boolean sideff;
	public byte direct;

	Node(BlockDef def, int in) {
		this.com = def.compiler;
		this.in = in > 0 ? new Node[in] : null;
		this.direct = (byte)in;
	}

	public Signal in(int i) {
		Node n = in[i];
		return n == null ? Signal.NULL : n.out;
	}

	/**Sets this node's result
	 * @param out */
	public void ret(Signal out) {
		this.out = out;
	}

	/**Sets this node's result with side-effect
	 * @param out */
	public void retSideff(Signal out) {
		this.out = out;
		this.sideff = true;
	}

	public Instruction compIn(Instruction ins, int i) throws Throwable {
		Node n = in[i];
		return n.sideff || n.out.isVar() ? n.compile(ins) : ins;
	}

	/**
	 * @throws Throwable   */
	public Instruction compile(Instruction last) throws Throwable {
		if (used++ > 0) return last;
		for (int i = 0; i < direct; i++) {
			Node n = in[i];
			if (n != null && (n.sideff || n.out.isVar()))
				last = n.compile(last);
		}
		return (Instruction)com.invokeExact(this, last);
	}

	@Override
	public String toString() {
		return out.toString();
	}

}
