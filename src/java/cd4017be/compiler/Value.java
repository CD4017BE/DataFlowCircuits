package cd4017be.compiler;


/**
 * 
 * @author CD4017BE */
public class Value {

	public final Signal decl;
	/** dynamic: id >= 0, const: id < 0 */
	public int id;

	public Value(Signal decl) {
		this.decl = decl;
	}

	public Signal with(Signal... args) {
		boolean se = false;
		for (Signal s : args) se |= s.sideeffect;
		return se ? new Signal(this, args) : decl;
	}

	public static boolean isConst(Signal... args) {
		for (Signal s : args)
			if (s.value.id >= 0) return false;
		return true;
	}

}
