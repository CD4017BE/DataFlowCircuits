package cd4017be.compiler.instr;

import cd4017be.compiler.Arguments;
import cd4017be.compiler.Instruction;
import cd4017be.compiler.Type;
import cd4017be.compiler.Value;
import cd4017be.compiler.builtin.ScopeData;


public class GetElementType implements Instruction {

	private final String name;
	private final int index;

	public GetElementType(String arg) {
		int index = -1;
		try {
			index = Integer.parseInt(arg);
		} catch (NumberFormatException e) {}
		this.index = index;
		this.name = index >= 0 || arg.isBlank() ? null : arg;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		Type t = args.in(0).type;
		int i = name != null ? t.index(name) : index;
		return new Value(i < 0 ? t : t.elem(i));
	}

}
