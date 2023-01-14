package cd4017be.compiler.builtin;

import cd4017be.compiler.Type;
import cd4017be.compiler.Value;

/**
 * 
 * @author CD4017BE */
public class ScopeData extends Value {

	public static final Type SCOPE = Type.builtin("scope");
	public static final ScopeData ROOT = new ScopeData(null);

	public final ScopeData parent;
	public final Value value;
	public final int path;

	public ScopeData(Value value) {
		this(null, 0, value);
	}

	public ScopeData(ScopeData parent, int path, Value value) {
		super(SCOPE, true);
		this.parent = parent;
		this.path = path;
		this.value = value;
	}

}
