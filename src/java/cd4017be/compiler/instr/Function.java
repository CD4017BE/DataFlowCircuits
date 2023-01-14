package cd4017be.compiler.instr;

import java.io.IOException;
import cd4017be.compiler.*;
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
		this.par = def.ins.length;
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
			cont.build(CircuitFile.readCircuit(is, def.module));
		}
		Node out = new Node();
		String[] outs = cont.collectOutputs(out);
		define(out);
		return outs;
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context, int idx) {
		Node node = new Node(this, Node.INSTR, par, idx);
		block.setIns(node);
		block.makeOuts(node, idx);
	}

}