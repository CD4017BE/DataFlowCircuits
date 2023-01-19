package cd4017be.compiler.builtin;

import java.util.ArrayList;
import cd4017be.compiler.Type;
import cd4017be.compiler.Value;

/**
 * 
 * @author CD4017BE */
public class ScopeData extends Value {

	public static final Type SCOPE = Type.builtin("scope");
	public static final ScopeData ROOT = new ScopeData(Bundle.VOID);

	public final ScopeData parent;
	public final Value value;
	public final int path;
	public final ArrayList<DynOp> dynOps = new ArrayList<>();

	public ScopeData(Value value) {
		this(null, 0, value);
	}

	public ScopeData(ScopeData parent, int path, Value value) {
		super(SCOPE);
		if (value == null) throw new NullPointerException();
		this.parent = parent;
		this.path = path;
		this.value = value;
	}

}
