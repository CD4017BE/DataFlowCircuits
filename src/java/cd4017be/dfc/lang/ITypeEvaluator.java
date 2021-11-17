package cd4017be.dfc.lang;

/**
 * @author CD4017BE */
public interface ITypeEvaluator {

	public void eval(CircuitFile file, int out, Node n) throws SignalError;

}
