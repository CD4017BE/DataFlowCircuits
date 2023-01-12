package cd4017be.compiler;

import static java.lang.Math.max;

import java.util.List;

public class CodeBlock {
	/** sequence of instructions to run */
	Instruction[] ops;
	/** arguments for each instruction: [][0] = output address, [][1...] = input addresses */
	int[][] args;
	/** [0] = skip destination, [1...] = branch destinations */
	final CodeBlock[] next;
	/** [0] = address of this block's scope, [1...] = addresses of outgoing scopes */
	final int[] scope;
	/** address of the switch argument or -1 if not branching */
	int swt;

	/**@param ops number of instructions
	 * @param br number of outgoing branches or 0 for linear flow */
	public CodeBlock(int br) {
		this.scope = new int[br + 1];
		this.next = new CodeBlock[max(2, br + 1)];
	}

	public void ops(List<Node> nodes) {
		int n = nodes.size();
		this.ops = new Instruction[n];
		this.args = new int[n][];
		for (int i = 0, k = n - 1; k >= 0; i++, k--) {
			Node node = nodes.get(k);
			this.ops[i] = node.op;
			int l = node.in.length;
			int[] args = new int[l + 1];
			args[0] = node.addr;
			for (int j = 0; j < l; j++)
				args[j + 1] = node.in[j].addr();
			this.args[i] = args;
		}
	}

	public CodeBlock next(int scope, CodeBlock skip, CodeBlock next) {
		this.scope[0] = scope;
		this.next[0] = skip;
		this.next[1] = next;
		this.swt = -1;
		return this;
	}

	public CodeBlock swt(int scope, int swt, CodeBlock skip) {
		this.scope[0] = scope;
		this.next[0] = skip;
		this.swt = swt;
		return this;
	}

	public CodeBlock br(int i, CodeBlock next, int scope) {
		this.next[i + 1] = next;
		this.scope[i + 1] = scope;
		return this;
	}

}