package cd4017be.compiler;

import java.util.HashMap;

import cd4017be.dfc.lang.BlockDef;

/**
 * 
 * @author CD4017BE */
public class Module {

	public final HashMap<String, Signal> signals = new HashMap<>();
	public final HashMap<String, BlockDef> blocks = new HashMap<>();

}
