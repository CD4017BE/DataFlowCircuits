package cd4017be.compiler;

import cd4017be.compiler.builtin.*;
import cd4017be.compiler.instr.Function;

public class Arguments {
	public static final int COMPUTE_LIMIT = 1000000;
	public static final Arguments EMPTY = new Arguments();

	final Value[] vars;
	private int[] args;
	private int limit;

	public Arguments(Value... param) {
		this.vars = param;
		this.args = new int[param.length + 1];
		for (int i = 0; i < args.length; i++)
			args[i] = i - 1;
	}

	public Arguments(Function f, Arguments args, ScopeData scope) {
		this.vars = new Value[f.vars.length];
		this.vars[0] = scope;
		for (int i = 1; i <= f.par; i++)
			this.vars[i] = args.vars[args.args[i]];
		this.limit = args.limit - 1;
	}

	public Arguments resetLimit() {
		this.limit = COMPUTE_LIMIT;
		return this;
	}

	public Value in(int i) {
		return get(args[i + 1]);
	}

	public int ins() {
		return args.length - 1;
	}

	public Value[] inArr(int i0) {
		Value[] arr = new Value[args.length - ++i0];
		for (int i = 0; i0 < args.length; i++, i0++)
			arr[i] = get(args[i0]);
		return arr;
	}

	public CodeBlock run(CodeBlock block) throws SignalError {
		if (--limit < 0) throw new SignalError(~block.args[0][0], "compute limit exceeded");
		Value v = vars[block.scope[0]];
		if (!(v instanceof ScopeData sd))
			return block.next[0];
		int l = block.ops.length;
		for (int i = 0; i < l; i++)
			vars[(args = block.args[i])[0]] = block.ops[i].eval(this, sd);
		if (block.swt < 0) return block.next[1];
		if (!(vars[block.swt] instanceof SwitchSelector ss))
			throw new SignalError(~block.swt, "switch selector expected");
		int p = ss.path;
		for (int i = 1; i < block.scope.length; i++) {
			int j = block.scope[i];
			if (j < 0) continue;
			vars[j] = p >= 0 && p != i - 1 ? null
				: ss.value == null ? sd
				: new ScopeData(sd, i - 1, ss.value);
		}
		if (p >= block.next.length - 1)
			throw new SignalError(~block.swt, "invalid branch path " + p);
		return block.next[p < 0 ? 1 : p + 1];
	}

	public Value get(int addr) {
		return addr < 0 ? Bundle.VOID : vars[addr];
	}

	public Value ret(Function f, Arguments args) {
		args.limit = limit;
		return get(f.ret);
	}

	public Value error(String msg) throws SignalError {
		throw new SignalError(~args[0], msg, null);
	}

	public Value error(String msg, Throwable cause) throws SignalError {
		throw new SignalError(~args[0], msg, cause);
	}

}