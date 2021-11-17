package cd4017be.dfc.lang;

import java.lang.invoke.MethodHandle;

import cd4017be.dfc.compiler.Instruction;

/**
 * @author CD4017BE */
public class Node {

	private static final byte DYNAMIC = 1, SIDEEFFECT = -1;

	public MethodHandle com;
	public Node[] in;
	public Signal[] out;
	public int used;
	public byte flags, direct;

	Node(BlockDef def, int in) {
		this.com = def.compiler;
		this.in = in > 0 ? new Node[in] : null;
		this.direct = (byte)in;
	}

	public Signal[] in(int i) {
		Node n = in[i];
		return n == null ? Signal.EMPTY : n.out;
	}

	/**Sets this node's result with normal side effect propagation.
	 * @param out */
	public void ret(Signal[] out) {
		this.out = out;
		flags = 0;
		if (in == null) return;
		for (Node n : in)
			if (n != null && n.flags < 0) {
				flags = SIDEEFFECT;
				return;
			}
		for (Signal s : out)
			if (!s.constant()) {
				flags = DYNAMIC;
				return;
			}
		
	}

	/**Sets this node's constant result with no side effect propagation.
	 * @param out the node's result */
	public void retConst(Signal[] out) {
		this.out = out;
		flags = 0;
	}

	/**Sets this node's result and introduces a side effect.
	 * @param out */
	public void retSideff(Signal[] out) {
		this.out = out;
		flags = SIDEEFFECT;
	}

	public Instruction compIn(Instruction ins, int i) throws Throwable {
		Node n = in[i];
		return n.flags == 0 ? ins : n.compile(ins);
	}

	/**
	 * @throws Throwable   */
	public Instruction compile(Instruction last) throws Throwable {
		if (used++ > 0) return last;
		for (int i = 0; i < direct; i++) {
			Node n = in[i];
			if (n != null && n.flags != 0)
				last = n.compile(last);
		}
		return (Instruction)com.invokeExact(this, last);
	}

	@Override
	public String toString() {
		return (flags < 0 ? "se:" : flags != 0 ? "dy:" : "cs:") + out;
	}

}
