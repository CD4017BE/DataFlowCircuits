package cd4017be.dfc.lang;

import java.util.Objects;

import cd4017be.dfc.graph.Node;

/**
 * @author CD4017BE */
public class SignalError extends Exception {

	private static final long serialVersionUID = 1L;

	public SignalError prev, next;
	public final Node node;
	public final int io;

	public SignalError(Node node, int io, Throwable cause) {
		super(cause.getLocalizedMessage(), cause);
		Objects.checkIndex(io, node.def.ioNames.length);
		this.node = node;
		this.io = io;
	}

	public SignalError(Node node, int io, String message) {
		super(message);
		Objects.checkIndex(io, node.def.ioNames.length);
		this.node = node;
		this.io = io;
	}

	@Override
	public String getLocalizedMessage() {
		BlockDef def = node.def;
		return "%s:%s: %s".formatted(def, def.ioNames[io], getMessage());
	}

	public void remove() {
		if (prev != null) prev.next = next;
		if (next != null) next.prev = prev;
		next = prev = null;
	}

	public void add(SignalError next) {
		if ((next.next = this.next) != null)
			next.next.prev = next;
		(this.next = next).prev = this;
	}

}
