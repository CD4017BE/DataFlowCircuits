package cd4017be.dfc.graph;


/**
 * 
 * @author CD4017BE */
public interface Macro {

	Node getOutput(Context c);

	/**Connect the given input of the given node according to circuit / macro expansion.
	 * @param n the node
	 * @param i the input pin index
	 * @param c the current context */
	void connectInput(Node n, int i, Context c);

	String[] arguments(Node n, int min);

	Node parent();

}
