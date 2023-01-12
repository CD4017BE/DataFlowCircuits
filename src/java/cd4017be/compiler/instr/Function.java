package cd4017be.compiler.instr;

import java.io.IOException;
import java.util.HashMap;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.ScopeData;
import cd4017be.util.ExtInputStream;

public class Function implements Instruction, NodeAssembler {

	private final BlockDef def;
	/** number of function parameters */
	private final int par;
	/** total number of data entries used */
	private int vars;
	/** address of return value */
	private int ret;
	/** first block to execute */
	private CodeBlock first;

	public Function(BlockDef def) {
		this.def = def;
		this.par = def.ins.length;
	}

	public Function(int par) {
		this.def = null;
		this.par = par;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		if (first == null) compile();
		Arguments varbuf = new Arguments(vars, par, args, scope);
		for (CodeBlock block = first; block != null;)
			block = varbuf.run(block);
		return varbuf.get(ret);
	}

	public void define(Node out) {
		this.vars = Node.evalScopes(out, par + 1);
		this.ret = out.in[0].addr();
		this.first = ((ScopeBranch)out.in[0].scope()).compile(null, null, -1);
	}

	public String[] compile() {
		BlockDesc[] blocks;
		try (ExtInputStream is = CircuitFile.readBlock(def)) {
			blocks = CircuitFile.readCircuit(is, def.module);
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
		HashMap<String, Node> links = new HashMap<>();
		//create output and input nodes
		for (int i = 0; i < def.ins.length; i++)
			links.put(def.ins[i], new Node(i));
		//assemble block nodes
		for (BlockDesc block : blocks)
			block.def.assembler.assemble(block, links);
		//connect blocks
		for (BlockDesc block : blocks)
			for (int i = 0; i < block.inLinks.length; i++) {
				int k = block.inLinks[i];
				if (k < 0) continue;
				block.ins[i].connect(blocks[k & 0xffff].outs[k >> 16]);
			}
		//connect macro outputs
		String[] outs = def.assembler instanceof ConstList
			? links.keySet().toArray(String[]::new) : def.outs;
		Node out;
		if (outs.length != 1) {
			out = new Node(null, Node.INSTR, outs.length);
			for (int i = 0; i < outs.length; i++)
				out.in[i].connect(links.get(outs[i]));
		} else out = links.get(outs[0]);
		//TODO remove named links
		//compile function
		define(new Node(out));
		return outs;
	}

	@Override
	public void assemble(BlockDesc block, HashMap<String, Node> namedLinks) {
		Node node = new Node(this, Node.INSTR, par);
		block.setIns(node);
		block.makeOuts(node);
	}

}