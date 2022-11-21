package cd4017be.compiler;

/**
 * 
 * @author CD4017BE */
public class Context {

	public MacroState stackFrame;
	public Scope scope = Scope.ROOT;
	public Value outVal;
	public SideEffects outSE;
	public NodeState[] inputs;
	public SignalError error;

	/**@param l maximum number of steps to run (must be >= 0)
	 * @return whether computation is finished */
	public boolean tick(int l) {
		for (MacroState frame; (frame = stackFrame) != null && --l >= 0;)
			frame.tick();
		return l >= 0;
	}

	public void clear() {
		stackFrame = null;
		outVal = null;
		outSE = null;
		error = null;
	}

}
