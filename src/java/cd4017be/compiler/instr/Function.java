package cd4017be.compiler.instr;

import java.io.IOException;
import cd4017be.compiler.*;
import cd4017be.compiler.builtin.Bundle;
import cd4017be.compiler.builtin.ScopeData;
import cd4017be.util.ExtInputStream;

public class Function implements Instruction, NodeAssembler {

	public final BlockDef def;
	/** number of function parameters */
	public final int par;
	/** length = total number of data entries used,
	 *  entries = address to block index lookup-table for error reporting */
	public int[] vars;
	/** address of return value */
	public int ret;
	/** first block to execute */
	public CodeBlock first;

	public Function(BlockDef def) {
		this.def = def;
		this.par = def.ins.length + (def.assembler instanceof ConstList ? 0 : def.args.length);
	}

	public Function(int par) {
		this.def = null;
		this.par = par;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) throws SignalError {
		try {
			if (first == null) compile();
			Arguments varbuf = new Arguments(this, args, scope);
			for (CodeBlock block = first; block != null;)
				block = varbuf.run(block);
			return varbuf.ret(this, args);
		} catch (SignalError e) {
			return args.error(null, e.resolvePos(vars));
		} catch (Throwable e) {
			return args.error(null, e);
		}
	}

	public void reset() {
		this.first = null;
		this.vars = null;
	}

	public void define(Node out) throws SignalError {
		this.vars = new int[Node.evalScopes(out, par + 1)];
		this.ret = out.in[0].addr();
		this.first = ((ScopeBranch)out.in[0].scope()).compile(null, null, -1, vars);
	}

	public String[] compile() throws IOException, SignalError {
		NodeContext cont = new NodeContext(def, false);
		try (ExtInputStream is = CircuitFile.readBlock(def)) {
			cont.build(CircuitFile.readCircuit(is, def.module), true);
		}
		Node out = new Node();
		String[] outs = cont.collectOutputs(out);
		define(out);
		return outs;
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
		Node node = new Node(this, Node.INSTR, par, idx);		
		int n = def.args.length;
		if (n > 0) {
			String[] args = context.args(block);
			for (int i = 0; i < n - 1; i++)
				node.in[i].connect(new Node(Value.parse(args[i], context, idx, def.args[i]), Node.INSTR, 0, idx));
			Value last;
			if (args.length == n) last = Value.parse(args[n - 1], context, idx, def.args[n - 1]);
			else {
				Value[] arr = new Value[args.length - n + 1];
				for (int i = 0; i < arr.length; i++)
					arr[i] = Value.parse(args[n - 1 + i], context, idx, def.args[n - 1]);
				last = new Bundle(arr);
			}
			node.in[n - 1].connect(new Node(last, Node.INSTR, 0, idx));
		}
		int m = def.ins.length - 1;
		for (int i = 0; i < m; i++)
			block.ins[i] = node.in[i + n];
		if (block.ins.length - m == 1)
			block.ins[m] = node.in[m + n];
		else {
			Node merge = new Node(Instruction.PACK, Node.INSTR, block.ins.length - m, idx);
			node.in[m].connect(merge);
			for (int i = 0; i < merge.in.length; i++)
				block.ins[m + i] = merge.in[i];
		}
		block.makeOuts(node, idx);
	}

}