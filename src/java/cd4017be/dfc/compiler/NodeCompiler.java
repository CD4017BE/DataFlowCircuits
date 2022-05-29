package cd4017be.dfc.compiler;

/**
 * 
 * @author CD4017BE */
@FunctionalInterface
public interface NodeCompiler {

	void compile(NodeInstruction ni, Compiler c) throws CompileError;

	NodeCompiler NULL = (ni, c) -> {};

}
