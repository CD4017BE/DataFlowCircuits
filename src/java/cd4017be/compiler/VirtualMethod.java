package cd4017be.compiler;

/**
 * @author CD4017BE */
@FunctionalInterface
public interface VirtualMethod {

	SignalError run(NodeState a, NodeState state);

	static SignalError revOp(NodeState a, NodeState ns, Value vb, String op) {
		VirtualMethod vm = vb.type.vtable.get(op);
		return vm != null ? vm.run(a, ns) : new SignalError("y doesn't support " + op);
	}
}