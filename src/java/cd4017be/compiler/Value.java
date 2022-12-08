package cd4017be.compiler;

import java.io.IOException;
import java.lang.invoke.MethodType;

import cd4017be.compiler.builtin.CstBytes;

/**
 * 
 * @author CD4017BE */
public class Value {

	public final Type type;

	public Value(Type type) {
		this.type = type;
	}

	protected Value(Type type, boolean check) {
		if (check && type.vtable.valueClass != getClass())
			throw new IllegalArgumentException("wrong type");
		this.type = type;
	}

	public SideEffects effect(SideEffects... args) {
		return new SideEffects(SideEffects.combine(args), null, this);
	}

	@Override
	public String toString() {
		return type.toString();
	}

	public int elCount() {
		return 0;
	}

	public Value element(int i) {
		return null;
	}

	/**@return bytes to serialize this value to a file */
	public CstBytes data() {
		return null;
	}

	static final MethodType DESERIALIZER
	= MethodType.methodType(Value.class, Type.class, byte[].class, Value[].class);

	public static Value deserialize(Type type, byte[] data, Value[] elements) throws IOException {
		if (data == null) return new Value(type);
		try {
			return (Value)type.vtable.deserializer.invokeExact(type, data, elements);
		} catch(Throwable e) {
			throw new IOException(e);
		}
	}

}
