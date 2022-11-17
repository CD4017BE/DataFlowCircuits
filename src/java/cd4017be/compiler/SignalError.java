package cd4017be.compiler;

/**
 * @author cd4017be */
public class SignalError {

	public final String msg;
	public final SignalError child;
	public SignalError next;
	SignalError prev;
	public int nodeId;

	public SignalError(Throwable cause) {
		this(cause.toString());
	}

	public SignalError(String msg) {
		this(msg, null);
	}

	public SignalError(String msg, SignalError child) {
		this.msg = msg;
		this.child = child;
	}

	public void record(NodeState ns) {
		nodeId = ns.node.idx;
		MacroState ms = ns.state;
		if (ns.error != null) ns.error.clear(ns);
		if ((next = ms.errors) != null) next.prev = this;
		ms.errors = ns.error = this;
	}

	public void clear(NodeState ns) {
		if (next != null) next.prev = prev;
		if (prev != null) prev.next = next;
		else ns.state.errors = next;
		ns.error = null;
	}

}
