package cd4017be.compiler;

import cd4017be.compiler.builtin.Bundle;

/**Used to schedule scope and signal updates on nodes.
 * @author CD4017BE */
public final class NodeState {

	public static final NodeState DISCONNECTED = new NodeState(null, null);
	static {DISCONNECTED.value = Bundle.VOID;}

	/** the state where the update is running in */
	public final MacroState state;
	/** the node being updated */
	public final Node node;
	public NodeState nextD, nextS;
	public Scope scope;
	public Value value;
	public SideEffects se;
	public SignalError error;
	/** bits[0..62]: missing inputs, bit[63]: missing scope */
	long update, usedIns;

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

	public NodeState in(int i) {
		i = node.ins[i];
		return i < 0 ? DISCONNECTED : state.states[i];
	}

	public void updateIn(int i) {
		if (scope != null && (update &= ~(1L << i)) == 0)
			scheduleD();
	}

	public Scope inScope(int i) {
		return (usedIns >> i & 1) != 0 ? scope : null;
	}

	public SignalError out(NodeState ns) {
		return ns != null ? out(ns.value, ns.se) : null;
	}

	public SignalError out(Value value, SideEffects se) {
		boolean chng = this.value != value || this.se != se;
		this.value = value;
		this.se = se;
		int[] outs = node.outs;
		NodeState[] states = state.states;
		for(int i = node.usedOuts - 1; i >= 0; i--) {
			int j = outs[i];
			NodeState ns = states[j & 0xffffff];
			if (ns != null && (chng || ns.update != 0))
				ns.updateIn(j >> 24);
		}
		return null;
	}

	public NodeState inScopeUpdate(int i) {
		usedIns |= 1L << i;
		int j = node.ins[i];
		if (j < 0) return DISCONNECTED;
		NodeState ns = state.states[j];
		if (ns != null && ns.value != null) return ns;
		update |= 1L << i;
		state.updateScope(j);
		return null;
	}

	public boolean scope(Scope s, long ins) {
		if (scope == s) return false;
		scope = s;
		value = null;
		se = null;
		int n = node.ins.length;
		update = usedIns = (1L << n) - 1L & ins;
		for (int i = 0; i < n; i++) {
			int j = node.ins[i];
			if ((ins >>> i & 1) == 0) {
				if (j < 0) continue;
				NodeState ns = state.states[j];
				if (ns != null) ns.scheduleS();
			} else if (j < 0) update ^= 1L << i;
			else state.updateScope(j);
		}
		return true;
	}

	public void updateS() {
		if (update >= 0) return;
		update ^= Long.MIN_VALUE;
		Scope s = null;
		int[] outs = node.outs;
		NodeState[] states = state.states;
		for (int i = node.usedOuts - 1; i >= 0; i--) {
			int o = outs[i];
			NodeState ns = states[o & 0xffffff];
			s = Scope.union(s, ns != null ? ns.inScope(o >>> 24) : null);
		}
		if (node.op instanceof NodeOperator.Scoped sno)
			sno.compScope(this, s);
		else scope(s, -1L);
		if (value != null) {
			out(value, se);
			return;
		}
		if (update == 0 && scope != null) scheduleD();
	}

	public void updateD() {
		if (scope != null && update == 0) try {
			SignalError error = node.op.compValue(this);
			if (error != null) error.record(this);
		} catch (Throwable e) {
			e.printStackTrace();
			new SignalError(e).record(this);
		}
	}

	public void scheduleS() {
		update |= Long.MIN_VALUE;
		if (nextS != null) return;
		NodeState ns = state.lastS;
		if (ns == this) return;
		if (ns != null) ns.nextS = this;
		else state.firstS = this;
		state.lastS = this;
	}

	public void scheduleD() {
		if (nextD != null) return;
		NodeState ns = state.lastD;
		if (ns == this) return;
		if (ns != null) ns.nextD = this;
		else state.firstD = this;
		state.lastD = this;
		if (error != null) error.clear(this);
	}

	public void remove() {
		update = Long.MAX_VALUE;
		if (error != null) error.clear(this);
		state.states[node.idx] = null;
	}

}