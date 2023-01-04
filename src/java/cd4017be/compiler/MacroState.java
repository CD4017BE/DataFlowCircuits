package cd4017be.compiler;

/**Stores runtime state of a macro program.
 * @author CD4017BE */
public class MacroState {

	public final NodeState parent;
	public final Context context;
	public final Macro macro;
	public NodeState[] states;
	public NodeState firstD, firstS;
	NodeState lastD, lastS;
	public SignalError errors;

	/**@param context
	 * @param macro root macro to run */
	public MacroState(Context context, Macro macro) { this(null, context, macro); }

	/**@param parent 
	 * @param macro */
	public MacroState(NodeState parent, Macro macro) { this(parent, parent.state.context, macro); }

	private MacroState(NodeState parent, Context context, Macro macro) {
		this.parent = parent;
		this.context = context;
		this.macro = macro.ensureLoaded(context);
		this.states = new NodeState[macro.nodeCount];
		context.stackFrame = this;
		updateScope(0);
	}

	public void updateScope(int i) {
		state(i).scheduleS();
	}

	public NodeState state(int i) {
		NodeState u = states[i];
		if (u != null) return u;
		return states[i] = new NodeState(this, macro.nodes[i]);
	}

	public void tick() {
		NodeState ns = firstS;
		while (ns != null) {
			ns.updateS();
			NodeState ns0 = ns;
			ns = ns0.nextS;
			ns0.nextS = null;
		}
		firstS = lastS = null;
		if ((ns = firstD) != null) {
			if ((firstD = ns.nextD) == null)
				lastD = null;
			else ns.nextD = null;
			ns.updateD();
		} else popError();
	}

	private void popError() {
		SignalError err = new SignalError("stalled", errors);
		if (parent == null) {
			context.stackFrame = null;
			context.error = err;
		} else {
			context.stackFrame = parent.state;
			err.record(parent);
		}
	}

	public Scope scope() {
		return parent != null ? parent.scope : context.scope;
	}

	public NodeState inVal(int i) {
		return parent != null ? parent.in(i) : context.inputs[i];
	}

	public SignalError pop(Value value, SideEffects se) {
		if (parent == null) {
			context.stackFrame = null;
			context.outVal = value;
			context.outSE = se;
			return null;
		} else {
			context.stackFrame = parent.state;
			return parent.out(value, se);
		}
	}

}
