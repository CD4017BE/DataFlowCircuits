package cd4017be.compiler;

import cd4017be.compiler.builtin.ScopeData;
import cd4017be.compiler.builtin.SwitchSelector;

public class Arguments {
	private final Value[] vars;
	private int[] args;

	public Arguments(Value... param) {
		this.vars = param;
		this.args = new int[param.length + 1];
		for (int i = 0; i < args.length; i++)
			args[i] = i - 1;
	}

	public Arguments(int vars, int par, Arguments args, ScopeData scope) {
		this.vars = new Value[vars];
		this.vars[0] = scope;
		for (int i = 1; i <= par; i++)
			this.vars[i] = args.vars[args.args[i]];
	}

	public Value in(int i) {
		return vars[args[i + 1]];
	}

	public int ins() {
		return args.length - 1;
	}

	public CodeBlock run(CodeBlock block) {
		Value v = vars[block.scope[0]];
		if (!(v instanceof ScopeData sd))
			return block.next[0];
		int l = block.ops.length;
		for (int i = 0; i < l; i++)
			vars[(args = block.args[i])[0]] = block.ops[i].eval(this, sd);
		if (!(block.swt >= 0 && vars[block.swt] instanceof SwitchSelector ss))
			return block.next[1];
		for (int i = 1; i < block.scope.length; i++) {
			int j = block.scope[i];
			if (j < 0) continue;
			vars[j] = ss.path >= 0 && ss.path != i - 1 ? null
				: new ScopeData(sd, i - 1, ss.value);
		}
		return block.next[ss.path < 0 ? 1 : ss.path + 1];
	}

	public Value get(int addr) {
		return (Value)vars[addr];
	}
}