package cd4017be.dfc.lang;

/**
 * @author cd4017be */
public abstract class Instruction {

	public abstract Instruction setIO(int[] io) throws SignalError;

	public abstract void eval(Interpreter ip, Value[] vars) throws SignalError;

	protected static void checkIO(int[] io, int expLen) throws SignalError {
		if (io.length != expLen) throw new SignalError(io[0], "wrong IO count");
	}

}
