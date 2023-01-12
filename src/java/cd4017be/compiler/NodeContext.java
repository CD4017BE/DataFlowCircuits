package cd4017be.compiler;

import java.util.HashMap;

/**
 * 
 * @author CD4017BE */
public class NodeContext {

	public final BlockDef def;
	public final HashMap<String, Node> links = new HashMap<>();
	public Arguments state;

	public NodeContext(BlockDef def) {
		this.def = def;
	}

	public Node getIO(String name) {
		return links.computeIfAbsent(name,
			n -> new Node(Instruction.PASS, Node.INSTR, 1)
		);
	}

}
