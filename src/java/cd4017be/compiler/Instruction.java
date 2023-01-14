package cd4017be.compiler;

import cd4017be.compiler.builtin.Bundle;
import cd4017be.compiler.builtin.ScopeData;

/**
 * 
 * @author CD4017BE */
public interface Instruction {

	/**@param args
	 * @param scope
	 * @return result */
	Value eval(Arguments args, ScopeData scope) throws SignalError;

	Instruction PASS = (args, scope) -> args.in(0);
	Instruction PACK = (args, scope) -> new Bundle(args.inArr(0));

}
