package cd4017be.compiler;

/**
 * @author CD4017BE */
@FunctionalInterface
public interface VirtualMethod {
	SignalError run(NodeState a, NodeState state);
}