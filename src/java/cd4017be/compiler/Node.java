package cd4017be.compiler;

import java.util.Arrays;

/**Forms the static structure of a macro program.
 * The signal and scope values of the Node during a running program are stored in {@link MacroState} objects.
 * @author CD4017BE */
public final class Node {

	/** the operation being performed */
	public final NodeOperator op;
	/** auxiliary data, typically used by {@link #op} */
	public final Object data;
	public final int[] ins;
	public int[] outs;
	public int usedOuts;
	public final int idx;

	/**@param op the operation to perform by this Node
	 * @param data auxiliary data associated with this Node (may be null)
	 * @param m the Macro this new Node is being assembled into
	 * @param idx */
	Node(NodeOperator op, Object data, int ins, int idx) {
		this.op = op;
		this.data = data;
		this.ins = new int[ins];
		this.idx = idx;
		Arrays.fill(this.ins, -1);
	}

	public int idx(int in) {
		return idx | in << 24;
	}

	public void addOut(int dst) {
		if (usedOuts >= outs.length)
			outs = Arrays.copyOf(outs, outs.length * 2);
		outs[usedOuts++] = dst;
	}

	public void remOut(int dst) {
		for (int i = usedOuts - 1; i >= 0; i--)
			if (outs[i] == dst)
				outs[i] = outs[--usedOuts];
	}

}
