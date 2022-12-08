package cd4017be.compiler.builtin;

import static cd4017be.compiler.LoadingCache.CORE;

import cd4017be.compiler.*;

/**
 * @author CD4017BE */
public class Bundle extends Value {

	public static final Type BUNDLE = new Type(CORE.findType("bundle"), 0).unique();
	public static final Value[] EMPTY = {};
	public static final Bundle VOID = new Bundle(EMPTY);

	public final Value[] values;

	public Bundle(Value[] values) {
		super(BUNDLE);
		this.values = values;
	}

	public Bundle(Type type, Value[] values) {
		super(type, true);
		this.values = values;
	}

	@Override
	public int elCount() {
		return values.length;
	}

	@Override
	public Value element(int i) {
		return values[i];
	}

	@Override
	public CstBytes data() {
		return CstBytes.EMPTY;
	}

	public static Value deserialize(Type type, byte[] data, Value[] elements) {
		return new Bundle(type, elements);
	}

}