package cd4017be.compiler;

/**
 * @author cd4017be */
@SuppressWarnings("serial")
public class SignalError extends Exception {

	public int pos;

	public SignalError(int pos, String msg, Throwable cause) {
		super(msg != null ? msg : cause.getMessage(), cause);
		this.pos = pos;
	}

	public SignalError(int pos, String msg) {
		this(pos, msg, null);
	}

	public SignalError resolvePos(int[] lut) {
		if (pos < 0) pos = lut[~pos];
		return this;
	}

}
