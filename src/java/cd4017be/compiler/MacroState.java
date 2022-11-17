package cd4017be.compiler;

/**Stores runtime state of a macro program.
 * @author CD4017BE */
public class MacroState {

	public final NodeState parent;
	public final Context context;
	public final Macro macro;
	public NodeState[] states;
	public NodeState first;
	NodeState last;
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
		NodeState ns = state(i);
		ns.update |= Long.MIN_VALUE;
		ns.schedule();
	}

	public NodeState state(int i) {
		NodeState u = states[i];
		if (u != null) return u;
		return states[i] = new NodeState(this, macro.nodes[i]);
	}

	public void tick() {
		NodeState ns = first;
		if (ns != null) {
			ns.update();
			if ((first = ns.next) == null)
				last = null;
			else ns.next = null;
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

	public Signal inVal(int i) {
		return parent != null ? parent.inVal(i) : context.inputs[i];
	}

	public SignalError pop(Signal val) {
		if (parent == null) {
			context.stackFrame = null;
			context.result = val;
			return null;
		} else {
			context.stackFrame = parent.state;
			return parent.outVal(val);
		}
	}

}
