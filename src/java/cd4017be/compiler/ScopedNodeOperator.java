package cd4017be.compiler;


/**
 * 
 * @author CD4017BE */
public interface ScopedNodeOperator extends NodeOperator {

	Scope compScope(NodeState state, Scope scope);

}
