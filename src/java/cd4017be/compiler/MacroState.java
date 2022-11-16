package cd4017be.compiler;

/**Stores runtime state of a macro program.
 * @author CD4017BE */
public class MacroState {

	public final NodeState parent;
	public final Context context;
	public final Macro macro;
	NodeState[] states;
	NodeState first, last;

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
		if (ns == null) pop(null);
		else {
			ns.update();
			if ((first = ns.next) == null)
				last = null;
			else ns.next = null;
		}
	}

	public void pop(Signal val) {
		if (parent == null) {
			context.stackFrame = null;
			context.result = val;
		} else {
			context.stackFrame = parent.state;
			parent.outVal(val);
		}
	}

}
