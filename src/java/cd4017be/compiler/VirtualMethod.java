package cd4017be.compiler;

/**
 * @author CD4017BE */
@FunctionalInterface
public interface VirtualMethod {
	void run(Signal a, NodeState state);
}