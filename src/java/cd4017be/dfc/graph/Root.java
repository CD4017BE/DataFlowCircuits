package cd4017be.dfc.graph;

import java.util.Arrays;

import cd4017be.dfc.lang.BlockDef;

/**
 * 
 * @author CD4017BE */
public class Root implements Macro {

	private final BlockDef def;

	public Root(BlockDef def) {
		this.def = def;
	}

	@Override
	public Node getOutput(Context c) {
		return new Node(this, 0, def, 1 + def.inCount);
	}

	@Override
	public void connectInput(Node n, int i, Context c) {
		n.connect(i, Node.NULL, c);
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
