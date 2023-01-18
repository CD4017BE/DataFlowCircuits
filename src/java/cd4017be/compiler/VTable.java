package cd4017be.compiler;

import static java.lang.invoke.MethodHandleProxies.asInterfaceInstance;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.HashMap;

import cd4017be.compiler.builtin.ScopeData;


/**
 * 
 * @author CD4017BE */
public class VTable extends HashMap<String, Instruction> {
	private static final long serialVersionUID = -2902223347716374669L;

	public final Module module;
	public final String id, name;
	public final int color;
	public final Class<? extends Value> valueClass;
	public final MethodHandle deserializer;

	public VTable(Module module, String id, String name, int color, Class<? extends Value> valueClass) {
		this.module = module;
		this.id = id;
		this.name = name;
		this.color = color;
		this.valueClass = valueClass;
		try {
			this.deserializer = MethodHandles.lookup().findStatic(valueClass, "deserialize", Value.DESERIALIZER);
		} catch(NoSuchMethodException | IllegalAccessException e1) {
			throw new IllegalArgumentException(e1);
		}
	}

	public void initInternal() {
		Lookup lu = MethodHandles.lookup();
		for (Method meth : valueClass.getDeclaredMethods()) {
			if (((PUBLIC | STATIC) & ~meth.getModifiers()) != 0) continue;
			if (meth.getReturnType() != Value.class) continue;
			Class<?>[] par = meth.getParameterTypes();
			if (par.length != 2 || par[0] != Arguments.class || par[1] != ScopeData.class) continue;
			if (containsKey(meth.getName())) continue;
			try {
				put(meth.getName(), asInterfaceInstance(Instruction.class, lu.unreflect(meth)));
			} catch(IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean equals(Object o) {
		return o == this || o instanceof VTable other
		&& other.module == module && other.id.equals(id);
	}

	@Override
	public int hashCode() {
		return module.hashCode() * 31 + id.hashCode();
	}

	@Override
	public String toString() {
		return module + ":" + id;
	}

}
