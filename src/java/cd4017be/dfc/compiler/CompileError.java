package cd4017be.dfc.compiler;

/**
 * @author CD4017BE */
public class CompileError extends Exception {

	private static final long serialVersionUID = 1L;

	public final int idx;

	public CompileError(int idx, String message) {
		super(message);
		this.idx = idx;
	}

	@Override
	public String getLocalizedMessage() {
		return getMessage() + " @Block " + idx;
	}

}
