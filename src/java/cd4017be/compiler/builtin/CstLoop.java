package cd4017be.compiler.builtin;

import cd4017be.compiler.Scope;
import cd4017be.compiler.Value;

/**
 * @author cd4017be */
public class CstLoop extends Scope {

	/** the value that is back-propagating (may be null) */
	public final Value value;

	public CstLoop(Scope parent, Value value) {
		super(parent);
		this.value = value;
	}

}
