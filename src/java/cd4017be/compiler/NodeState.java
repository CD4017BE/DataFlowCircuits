package cd4017be.compiler;

/**Used to schedule scope and signal updates on nodes.
 * @author CD4017BE */
public final class NodeState {
	/** the state where the update is running in */
	public final MacroState state;
	/** the node being updated */
	public final Node node;
	public NodeState next;
	public Scope scope;
	public Signal signal;
	/** bits[0..62]: missing inputs, bit[63]: missing scope */
	long update;

	NodeState(MacroState state, Node node) {
		this.state = state;
		this.node = node;
	}

	public Object data() {
		return node.data;
	}

	public int ins() {
		return node.ins.length;
	}

	public Signal inVal(int i) {
		i = node.ins[i];
		return i < 0 ? Signal.DISCONNECTED : state.states[i].signal;
	}

	public void outVal(Signal val) {
		boolean chng = signal != val;
		signal = val;
		int[] outs = node.outs;
		NodeState[] states = state.states;
		for(int i = node.usedOuts - 1; i >= 0; i--) {
			int j = outs[i];
			NodeState ns = states[j & 0xffffff];
			if (ns == null || ns.scope == null) continue;
			if ((chng || ns.update != 0) && (ns.update &= ~(1L << (j >> 24))) == 0)
				ns.schedule();
		}
	}

	public void update() {
		if (update < 0) {
			update ^= Long.MIN_VALUE;
			Scope s = null;
			int[] outs = node.outs;
			NodeState[] states = state.states;
			for (int i = node.usedOuts - 1; i >= 0; i--) {
				NodeState ns = states[outs[i] & 0xffffff];
				s = Scope.union(s, ns == null ? null : ns.scope);
			}
			if (node.op instanceof ScopedNodeOperator sno)
				s = sno.compScope(this, s);
			if (scope != s) {
				scope = s;
				signal = null;
				int n = node.ins.length;
				update = (1L << n) - 1L;
				for (int i = 0; i < n; i++) {
					int j = node.ins[i];
					if (j < 0) update ^= 1L << i;
					else state.updateScope(j);
				}
			} else if (signal != null) {
				outVal(signal);
				return;
			}
		}
		if (update == 0 && scope != null)
			node.op.compValue(this);
	}

	public void schedule() {
		if (next != null) return;
		NodeState ns = state.last;
		if (ns == this) return;
		if (ns != null) ns.next = this;
		else state.first = this;
		state.last = this;
	}

}