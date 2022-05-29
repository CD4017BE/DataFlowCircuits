package cd4017be.dfc.graph;

import cd4017be.dfc.lang.*;

/**
 * 
 * @author CD4017BE */
@FunctionalInterface
public interface Behavior {

	void update(Node node, Context c) throws SignalError;

	static Behavior NULL = (node, c) -> node.updateOutput(Signal.NULL, c);

}
