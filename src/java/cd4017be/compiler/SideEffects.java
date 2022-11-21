package cd4017be.compiler;

/**
 * @author CD4017BE */
public class SideEffects {
	/**optional side-effects to compute */
	public final SideEffects a, b;
	/**optional value to compute after {@link #a} and before {@link #b} */
	public final Value v;

	public SideEffects(SideEffects a, SideEffects b, Value v) {
		this.a = a;
		this.b = b;
		this.v = v;
	}

	public static SideEffects combine(SideEffects... args) {
		SideEffects se = null;
		for (SideEffects se1 : args)
			if (se1 != null)
				if (se == null) se = se1;
				else if (se1 != se)
					se = new SideEffects(se, se1, null);
		return se;
	}

}