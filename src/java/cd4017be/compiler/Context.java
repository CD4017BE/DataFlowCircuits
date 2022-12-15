package cd4017be.compiler;

import java.util.Arrays;

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

	public void run(Macro macro) {
		if (inputs == null || inputs.length != macro.ins())
			Arrays.fill(inputs = new NodeState[macro.ins()], NodeState.DISCONNECTED);
		this.stackFrame = new MacroState(this, macro);
		if (macro instanceof MutableMacro mm)
			mm.state = stackFrame;
	}

	public void setInputs(Value... ins) {
		this.inputs = new NodeState[ins.length];
		for (int i = 0; i < ins.length; i++)
			(inputs[i] = new NodeState(null, null)).value = ins[i];
	}

}
