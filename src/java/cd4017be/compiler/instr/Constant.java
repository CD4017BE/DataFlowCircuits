package cd4017be.compiler.instr;

import java.nio.CharBuffer;
import cd4017be.compiler.*;


/**
 * 
 * @author CD4017BE */
public class Constant implements NodeAssembler {

	private final Value value;

	public Constant(Value value) {
		this.value = value;
	}

	public Node node() {
		return new Node(value, Node.INSTR, 0);
	}

	@Override
	public void assemble(BlockDesc block, NodeContext context) {
		if (block.ins.length != 0 || block.args.length != block.outs.length)
			throw new IllegalArgumentException("wrong IO count");
		for (int i = 0; i < block.args.length; i++)
			try {
				CharBuffer buf = CharBuffer.wrap(block.args[0]);
				Value val = Value.parse(buf, context.def.module);
				if (buf.hasRemaining()) throw new IllegalArgumentException("unexpected symbols " + buf);
				block.outs[i] = new Node(val, Node.INSTR, 0);
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("can't parse expression: " + e.getMessage());
			}
	}

}
