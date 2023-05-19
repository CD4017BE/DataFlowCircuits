package cd4017be.dfc.lang;

import java.net.MalformedURLException;
import java.net.URL;
import cd4017be.dfc.editor.Main;
import cd4017be.dfc.graphics.SpriteModel;

/**
 * 
 * @author CD4017BE */
public class BlockDef {

	public static final String[] EMPTY_IO = {};
	static final int VAR_OUT = 1, VAR_IN = 2, VAR_ARG = 4;

	public final Module module;
	public final String id, modelId;
	public final String[] ins, outs, args;
	public final ArgumentParser[] parsers;
	public final NodeAssembler assembler;
	public final int vaSize;
	public SpriteModel model;
	public String name;
	public Instruction impl;

	public BlockDef(
		Module module, String id, String type, String model,
		String[] ins, String[] outs, String[] args, String[] argtypes
	) {
		this.module = module;
		this.id = id;
		this.assembler = module.assemblers.get(type).apply(this);
		this.ins = ins;
		this.outs = outs;
		this.args = args;
		int l = args.length;
		if (l != argtypes.length) throw new IllegalArgumentException();
		this.parsers = new ArgumentParser[l];
		for (int i = 0; i < l; i++)
			parsers[i] = module.parsers.get(argtypes[i]);
		this.modelId = model;
		this.vaSize = isVar(ins) & VAR_IN | isVar(outs) & VAR_OUT | isVar(args) & VAR_ARG;
		module.blocks.put(id, this);
	}

	private static int isVar(String[] io) {
		int l = io.length - 1;
		return l >= 0 && io[l].indexOf('#') >= 0 ? -1 : 0;
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
		return module + ":" + id;
	}

	public SpriteModel loadModel() {
		if (model != null) return model;
		String s = modelId;
		int i = s.indexOf(':');
		Module m = module;
		if (i >= 0) {
			if ((m = module.imports.get(s.substring(0, i))) == null) {
				System.out.println("module for block model not defined: " + s);
				return model = Main.ICONS.missing();
			}
			s = s.substring(i + 1);
		}
		if (!s.isEmpty()) try {
			return model = Main.ICONS.get(new URL(m.source, "icons/" + s + ".tga"));
		} catch(MalformedURLException e) {
			e.printStackTrace();
		}
		return model = Main.ICONS.missing();
	}

}
