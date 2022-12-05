package cd4017be.compiler;

import java.util.HashMap;

/**
 * 
 * @author CD4017BE */
public class Context {

	public final NodeState disconnected;
	public final HashMap<Type, Type> types;
	public MacroState stackFrame;
	public Scope scope = Scope.ROOT;
	public Value outVal;
	public SideEffects outSE;
	public NodeState[] inputs;
	public SignalError error;

	public Context(HashMap<Type, Type> types) {
		this.disconnected = new NodeState(null, null);
		this.types = types;
	}

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
		this.stackFrame = new MacroState(this, macro);
	}

	public void setInputs(Module m, Value... ins) {
		VTable vt = m.findType("void");
		if (vt == null) vt = new VTable(m, "void", "void", 0);
		disconnected.value = new Value(new Type(vt, 0).unique(types), null);
		this.inputs = new NodeState[ins.length];
		for (int i = 0; i < ins.length; i++)
			(inputs[i] = new NodeState(null, null)).value = ins[i];
	}

}
