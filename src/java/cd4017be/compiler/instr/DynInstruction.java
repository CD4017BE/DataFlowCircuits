package cd4017be.compiler.instr;

import cd4017be.compiler.Arguments;
import cd4017be.compiler.Instruction;
import cd4017be.compiler.Value;
import cd4017be.compiler.builtin.DynOp;
import cd4017be.compiler.builtin.ScopeData;

/**
 * @author cd4017be */
public class DynInstruction implements Instruction {

	private final String opCode;

	public DynInstruction(String opCode) {
		this.opCode = opCode;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		DynOp op = new DynOp(args.in(0).type, opCode, args.inArr(1));
		scope.dynOps.add(op);
		return op;
	}

}
