package cd4017be.compiler;

import cd4017be.compiler.builtin.ScopeData;

/**
 * 
 * @author CD4017BE */
public interface Instruction {

	/**@param args
	 * @param scope
	 * @return result */
	Value eval(Arguments args, ScopeData scope);

	Instruction PASS = (args, scope) -> args.get(0);

}
