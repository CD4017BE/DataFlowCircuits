package cd4017be.compiler.instr;

import cd4017be.compiler.Arguments;
import cd4017be.compiler.Instruction;
import cd4017be.compiler.Type;
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
		Type t = args.in(0).type;
		Value[] ins = new Value[args.ins() - 1];
		for (int i = 0; i < ins.length; i++)
			ins[i] = args.in(i + 1);
		return new DynOp(t, opCode, ins);
	}

}
