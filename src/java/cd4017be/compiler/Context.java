package cd4017be.compiler;

import cd4017be.dfc.lang.BlockRegistry;

/**
 * 
 * @author CD4017BE */
public class Context {

	public final BlockRegistry reg;
	public MacroState stackFrame;
	public Signal result;

	public Context(BlockRegistry reg) {
		this.reg = reg;
	}

	/**@param l maximum number of steps to run (must be >= 0)
	 * @return whether computation is finished */
	public boolean tick(int l) {
		for (MacroState frame; (frame = stackFrame) != null && --l >= 0;)
			frame.tick();
		return l >= 0;
	}

	public void clear() {
		stackFrame = null;
		result = null;
	}

}
