package cd4017be.dfc.lang;

/**
 * @author CD4017BE */
public class SignalError extends Exception {

	private static final long serialVersionUID = 1L;

	public final int block, in;

	public SignalError(int idx, int in, Throwable cause) {
		super(cause.getLocalizedMessage(), cause);
		this.block = idx;
		this.in = in;
	}

	public SignalError(int idx, int in, String message) {
		super(message);
		this.block = idx;
		this.in = in;
	}

	@Override
	public String getLocalizedMessage() {
		return "block%d:%d %s".formatted(block, in, getMessage());
	}

}
