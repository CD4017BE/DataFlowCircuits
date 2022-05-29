package cd4017be.dfc.compiler;

import cd4017be.dfc.graph.Node;

/**
 * @author CD4017BE */
public class CompileError extends Exception {

	private static final long serialVersionUID = 1L;

	public final int idx;

	public CompileError(Node node, String message) {
		super(message);
		this.idx = node.idx;
	}

	@Override
	public String getLocalizedMessage() {
		return getMessage() + " @Block " + idx;
	}

}
