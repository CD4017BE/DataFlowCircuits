package cd4017be.dfc.graph;

import java.util.Arrays;

import cd4017be.dfc.lang.BlockDef;

/**
 * 
 * @author CD4017BE */
public class Root implements Macro {

	public final Node node;

	public Root(BlockDef def) {
		this.node = new Node(this, 0, def);
	}

	@Override
	public Pin getOutput(int i, Context c) {
		return new Pin(node, i);
	}

	@Override
	public void connectInput(Node n, int i, Context c) {
		n.connect(i + n.out.length, Node.NULL, 0, c);
	}

	@Override
	public String[] arguments(Node n, int min) {
		String[] args = new String[min];
		Arrays.fill(args, "");
		return args;
	}

	@Override
	public Node parent() {
		return null;
	}

}
