package cd4017be.dfc.graph;

import cd4017be.dfc.lang.BlockDef;

/**
 * 
 * @author CD4017BE */
public record BlockInfo(BlockDef def, String[] arguments, int[] inputs) {}
