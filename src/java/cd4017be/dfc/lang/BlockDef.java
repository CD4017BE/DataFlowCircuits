package cd4017be.dfc.lang;

import java.net.MalformedURLException;

import cd4017be.dfc.graphics.SpriteModel;

/**
 * 
 * @author CD4017BE */
public class BlockDef {

	static final int VAR_OUT = 1, VAR_IN = 2, VAR_ARG = 4;

	public final Module module;
	public final String id, type, modelId;
	public final String[] ins, outs, args;
	public final NodeAssembler assembler;
	public final int vaSize;
	public SpriteModel model;
	public String name;
	public Instruction impl;

	public BlockDef(Module module, String id, String type, String[] ins, String[] outs, String[] args, String model, int vaSize) {
		this.module = module;
		this.id = id;
		this.ins = ins;
		this.outs = outs;
		this.args = args;
		this.modelId = model;
		this.vaSize = vaSize;
		this.type = type;
		this.assembler = module.assembler(type, this);
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
				return model = LoadingCache.ATLAS.get(null);
			}
			s = s.substring(i + 1);
		}
		if (!s.isEmpty()) try {
			return model = LoadingCache.ATLAS.get(m.path.resolve("icons/" + s + ".tga").toUri().toURL());
		} catch(MalformedURLException e) {
			e.printStackTrace();
		}
		return model = LoadingCache.ATLAS.get(null);
	}

}
