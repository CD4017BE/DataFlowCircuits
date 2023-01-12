package cd4017be.compiler.instr;

import java.util.Arrays;
import cd4017be.compiler.Arguments;
import cd4017be.compiler.Instruction;
import cd4017be.compiler.Module;
import cd4017be.compiler.Type;
import cd4017be.compiler.VTable;
import cd4017be.compiler.Value;
import cd4017be.compiler.builtin.Bundle;
import cd4017be.compiler.builtin.ScopeData;


public class NewType implements Instruction {

	private final VTable vtable;
	private final String[] names;
	private final int n;

	public NewType(String[] args, Module m) {
		String arg = args[0];
		int i = arg.lastIndexOf('#');
		if (i >= 0) {
			this.n = Integer.parseInt(arg.substring(i + 1));
			arg = arg.substring(0, i);
		} else this.n = 0;
		VTable vt = m.types.get(arg);
		this.vtable = vt != null ? vt : Bundle.BUNDLE.vtable;
		this.names = Arrays.copyOfRange(args, 1, args.length);
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		Type[] elem = new Type[names.length];
		for (int j = 0; j < elem.length; j++)
			elem[j] = args.in(j).type;
		return new Value(Type.of(vtable, names, elem, n));
	}

}
