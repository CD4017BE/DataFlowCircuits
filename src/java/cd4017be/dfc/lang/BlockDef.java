package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.LoadingCache.LOADER;

import java.nio.file.Path;
import java.util.function.Function;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.graphics.SpriteModel;
import cd4017be.dfc.lang.builders.ConstList;
import cd4017be.dfc.lang.instructions.IntrinsicLoader;
import modules.dfc.module.Intrinsics;

/**
 * 
 * @author CD4017BE */
public class BlockDef {

	public static final String[] EMPTY_IO = {};
	public static final ArgumentParser[] EMPTY_ARG = {};
	static final int VAR_OUT = 1, VAR_IN = 2, VAR_ARG = 4;

	public final Module module;
	public final String id;
	public String[] ins, outs, args;
	public ArgumentParser[] parsers;
	public NodeAssembler assembler;
	public Path modelPath;
	public SpriteModel model;
	public String name;
	public Instruction impl;
	public int vaSize;
	private boolean defined;

	BlockDef(Module module, String id) {
		this.module = module;
		this.id = id;
	}

	public BlockDef define(
		Function<BlockDef, NodeAssembler> assembler, String[] ins, String[] outs,
		String[] args, ArgumentParser[] parsers, Path model, String name
	) {
		if (defined) throw new IllegalStateException("allready defined " + this);
		this.ins = ins;
		this.outs = outs;
		this.args = args;
		this.parsers = parsers;
		this.modelPath = model;
		this.name = name;
		this.vaSize = isVar(ins) & VAR_IN | isVar(outs) & VAR_OUT | isVar(args) & VAR_ARG;
		this.assembler = assembler.apply(this);
		IntrinsicLoader.linkBlock(this, module.moduleImpl);
		this.defined = true;
		return this;
	}

	public ConstList defineModule() {
		return (ConstList)define(
			ConstList::new, EMPTY_IO, EMPTY_IO, EMPTY_IO, EMPTY_ARG,
			(LOADER == null ? module : LOADER).icon("module"), "Module Definition"
		).assembler;
	}

	public BlockDef defined() {
		if (defined) return this;
		if (Intrinsics.loadBlock(this)) return this;
		System.err.printf("missing block definition %s\n", this);
		return define(Intrinsics.ERROR, EMPTY_IO, EMPTY_IO, EMPTY_IO, EMPTY_ARG, null, "undefined block");
	}

	private static int isVar(String[] io) {
		int l = io.length - 1;
		return l >= 0 && io[l].indexOf('#') >= 0 ? -1 : 0;
	}

	public boolean isModule() {
		return id.isEmpty();
	}

	public int ins(int size) {
		int l = ins.length;
		return (vaSize & VAR_IN) == 0 ? l : l + size - 1;
	}

	public int outs(int size) {
		int l = outs.length;
		return (vaSize & VAR_OUT) == 0 ? l : l + size - 1;
	}

	public int args(int size) {
		int l = args.length;
		return (vaSize & VAR_ARG) == 0 ? l : l + size - 1;
	}

	public boolean varSize() {
		return vaSize != 0;
	}

	@Override
	public String toString() {
		return isModule() ? module.toString() : module + "/" + id;
	}

	public SpriteModel loadModel() {
		defined();
		if (model != null) return model;
		return model = modelPath != null ? Main.ICONS.get(modelPath) : Main.ICONS.missing();
	}

}
