package cd4017be.compiler.builtin;

import cd4017be.compiler.Type;
import cd4017be.compiler.Value;

/**
 * 
 * @author CD4017BE */
public class SwitchSelector extends Value {

	public static final Type SWITCH = Type.builtin("switch");

	public final Value value;
	public final int path;

	public SwitchSelector(int path, Value value) {
		super(SWITCH, true);
		this.path = path;
		this.value = value;
	}

}
