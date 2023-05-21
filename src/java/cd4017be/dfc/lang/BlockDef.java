package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.LoadingCache.LOADER;

import java.nio.file.Path;
import java.util.function.Function;

import cd4017be.dfc.editor.Main;
import cd4017be.dfc.graphics.SpriteModel;
import cd4017be.dfc.lang.builders.ConstList;

/**
 * 
 * @author CD4017BE */
public class BlockDef {

	public static final String[] EMPTY_IO = {};
	static final int VAR_OUT = 1, VAR_IN = 2, VAR_ARG = 4;

	public final Module module;
	public final String id;
	public final Path modelPath;
	public final String[] ins, outs, args;
	public final ArgumentParser[] parsers;
	public final NodeAssembler assembler;
	public final int vaSize;
	public SpriteModel model;
	public String name;
	public Instruction impl;

	public BlockDef(Module module) {
		this(
			module, "", ConstList::new,
			EMPTY_IO, EMPTY_IO, EMPTY_IO, new ArgumentParser[0],
			LOADER.icon("module"), "Module Definition"
		);
	}

	public BlockDef(
		Module module, String id, Function<BlockDef, NodeAssembler> assembler,
		String[] ins, String[] outs, String[] args, ArgumentParser[] parsers,
		Path model, String name
	) {
		this.module = module;
		this.id = id;
		this.ins = ins;
		this.outs = outs;
		this.args = args;
		this.parsers = parsers;
		this.modelPath = model;
		this.name = name;
		this.vaSize = isVar(ins) & VAR_IN | isVar(outs) & VAR_OUT | isVar(args) & VAR_ARG;
		this.assembler = assembler.apply(this);
		module.blocks.put(id, this);
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
		if (model != null) return model;
		return model = modelPath != null ? Main.ICONS.get(modelPath) : Main.ICONS.missing();
	}

}
