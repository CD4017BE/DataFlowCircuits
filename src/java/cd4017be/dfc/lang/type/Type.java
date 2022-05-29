package cd4017be.dfc.lang.type;

import cd4017be.dfc.compiler.NodeInstruction;
import cd4017be.dfc.graph.Node;
import cd4017be.dfc.lang.Signal;

/**
 * @author CD4017BE */
public interface Type {

	int sizeOf();
	int align();

	boolean canSimd();

	int color(Signal s);

	default boolean canAssignTo(Type t) {
		return t == this;
	}

	default boolean canBitcastTo(Type t) {
		return sizeOf() == t.sizeOf();
	}

	Signal getElement(Signal s, long i);

	default int getIndex(String name) {
		return -1;
	}

	default boolean canArithmetic() {
		return false;
	}

	default boolean canLogic() {
		return false;
	}

	default boolean canCompare() {
		return false;
	}

	default boolean dynamic() {
		return false;
	}

	default void evalConst(NodeInstruction ni, Object val) {
		if (val instanceof Node n)
			new NodeInstruction(ni, n, ni.after);
	}

	StringBuilder displayString(StringBuilder sb, boolean nest);

}
