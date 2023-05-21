package cd4017be.dfc.lang.instructions;

import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;
import modules.loader.Intrinsics;

/**
 * @author cd4017be */
public class VirtualCallIns extends Instruction {

	private final String[] names;
	private int[] io;

	public VirtualCallIns(String[] names) {
		this.names = names;
	}

	@Override
	public Instruction setIO(int[] io) throws SignalError {
		checkIO(io, names.length + 2);
		this.io = io;
		return this;
	}

	@Override
	public void eval(Interpreter ip, Value[] vars) throws SignalError {
		for (int i = 0; i < names.length; i++) {
			Instruction ins = vars[io[i + 2]].type.lookup(names[i]);
			if (ins == null) continue;
			ins.setIO(io).eval(ip, vars);
			if (vars[io[0]] != Intrinsics.NULL) return;
		}
		throw dynCallError(vars);
	}

	private SignalError dynCallError(Value[] vars) {
		StringBuilder sb = new StringBuilder();
		sb.append('(').append(vars[io[2]].type);
		for (int i = 3; i < io.length; i++)
			sb.append(", ").append(vars[io[i]].type);
		sb.append(") don't implement (").append(names[0]);
		for (int i = 1; i < names.length; i++)
			sb.append(", ").append(names[i]);
		sb.append(')');
		return new SignalError(~io[0], sb.toString());
	}

}
