package cd4017be.compiler.builtin;

import static cd4017be.compiler.LoadingCache.CORE;

import cd4017be.compiler.Type;
import cd4017be.compiler.Value;

/**
 * @author cd4017be */
public class SignalError extends Value {

	public static final Type ERROR = Type.of(CORE.findType("error"), 0);

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
		super(ERROR, true);
		this.msg = msg;
		this.child = child;
	}

//	public void record(NodeState ns) {
//		nodeId = ns.node.idx;
//		MacroState ms = ns.state;
//		if (ns.error != null) ns.error.clear(ns);
//		if ((next = ms.errors) != null) next.prev = this;
//		ms.errors = ns.error = this;
//	}
//
//	public void clear(NodeState ns) {
//		if (next != null) next.prev = prev;
//		if (prev != null) prev.next = next;
//		else ns.state.errors = next;
//		ns.error = null;
//	}

	@Override
	public String toString() { 
		return child == null ? msg : msg + "->" + child;
	}

}
