package cd4017be.dfc.lang.instructions;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Interpreter;
import cd4017be.dfc.lang.Module;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Type;
import cd4017be.dfc.lang.Value;

/**
 * @author cd4017be */
public class IntrinsicLoader {

	private static final boolean DEBUG = false;

	/**Annotation for public static methods implementing intrinsic block behavior.
	 * The method should have an {@link #useIp() optional} Interpreter parameter,
	 * followed by an {@link #useScope() optional} scope parameter,
	 * followed by {@link #inputs()} number of input parameters.
	 * <p> The scope and input parameters can be of type {@link Value},
	 * {@link Value#type Type}, {@link Value#elements Value[]}, {@link Value#data byte[]},
	 * {@link Value#value long}, {@link Value#value double} or {@link Value#value int}
	 * being automatically converted if necessary. </p>
	 * <p> If the return type is {@link Value}, it will be taken as output value.
	 * If the return type is void, the first input is taken as output value. </p>
	 * @author cd4017be */
	@Retention(RUNTIME)
	@Target(METHOD)
	public @interface Impl {
		/**@return whether the method uses the {@link Interpreter} instance as first parameter. */
		boolean useIp() default false;
		/**@return whether the method uses the scope value as parameter before the input value parameters */
		boolean useScope() default false;
		/**@return the number of method paramters that take input values */
		int inputs();
		/**@return the name of a public static {@link Type} field within the current class
		 * that provides the output value's {@link Value#type} if the method's return type is not {@link Value}. */
		String outType() default "";
	}

	@Retention(RUNTIME)
	@Target(METHOD)
	public @interface Init {}

	private static final String
	T_IMPL = getInternalName(IntrinsicLoader.class) + "$IMPL",
	T_INSTRUCTION = getInternalName(Instruction.class),
	T_VALUE = getInternalName(Value.class),
	T_INTERPRETER = getInternalName(Interpreter.class),
	T_DOUBLE = getInternalName(Double.class),
	T_SIGNALERROR = getInternalName(SignalError.class),
	T_RUNTIMEEXCEPTION = getInternalName(RuntimeException.class),
	D_SETIO = "([I)L" + T_INSTRUCTION + ";",
	D_CHECKIO = "([II)V",
	D_EVAL = "(L" + T_INTERPRETER + ";[L" + T_VALUE + ";)V",
	D_TYPE = getDescriptor(Type.class),
	D_ELEMENTS = getDescriptor(Value[].class),
	D_DATA = getDescriptor(byte[].class),
	D_OF = "(" + D_TYPE + ")L" + T_VALUE + ";",
	D_OFELEMENTS = "(" + D_ELEMENTS + D_TYPE + ")L" + T_VALUE + ";",
	D_OFDATA = "(" + D_DATA + D_TYPE + ")L" + T_VALUE + ";",
	D_OFVALUE = "(J" + D_TYPE + ")L" + T_VALUE + ";",
	D_NEWSIGNALERROR = "(I" + getDescriptor(String.class) + getDescriptor(Throwable.class) + ")V";
	private static final String[] EX_SIGNALERROR = {T_SIGNALERROR};

	public static MethodHandle linkIntrinsic(Method m, Impl an) {
		check(m, an);
		ClassWriter cw = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
		cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, T_IMPL, null, T_INSTRUCTION, null);
		cw.visitSource("IntrinsicLoader.java", null);
		addIdxField(cw, "o");
		if (an.useScope())
			addIdxField(cw, "s");
		for (int i = 0; i < an.inputs(); i++)
			addIdxField(cw, "i" + i);
		addConstructor(cw);
		addSetIO(cw, an);
		addEval(cw, m, an);
		cw.visitEnd();
		try {
			Lookup lookup = MethodHandles.lookup().defineHiddenClass(cw.toByteArray(), true);
			return lookup.findConstructor(lookup.lookupClass(), methodType(void.class));
		} catch (IllegalAccessException | NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void addConstructor(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESPECIAL, T_INSTRUCTION, "<init>", "()V", false);
		mv.visitInsn(RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private static void check(Method m, Impl an) {
		if ((~m.getModifiers() & (Modifier.STATIC | Modifier.PUBLIC)) != 0)
			throw new IllegalArgumentException("method must be public static");
		Class<?>[] par = m.getParameterTypes();
		if (par.length != an.inputs() + (an.useIp() ? 1 : 0) + (an.useScope() ? 1 : 0))
			throw new IllegalArgumentException("wrong number of parameters");
	}

	private static void bconst(MethodVisitor mv, int i) {
		if (i >= -1 && i <= 5)
			mv.visitInsn(ICONST_0 + i);
		else mv.visitIntInsn(BIPUSH, i);
	}

	private static void addIdxField(ClassWriter cw, String name) {
		cw.visitField(ACC_PRIVATE, name, "I", null, null);
	}

	private static void setIdxField(MethodVisitor mv, String name, int idx) {
		mv.visitVarInsn(ALOAD, 0);//this
		mv.visitVarInsn(ALOAD, 1);//int[] io
		bconst(mv, idx);
		mv.visitInsn(IALOAD);//io[idx]
		mv.visitFieldInsn(PUTFIELD, T_IMPL, name, "I");
	}

	private static void addSetIO(ClassWriter cw, Impl an) {
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "setIO", D_SETIO, null, EX_SIGNALERROR);
		mv.visitCode();
		mv.visitVarInsn(ALOAD, 1);
		bconst(mv, an.inputs() + 2);
		mv.visitMethodInsn(INVOKESTATIC, T_INSTRUCTION, "checkIO", D_CHECKIO, false);
		setIdxField(mv, "o", 0);
		if (an.useScope()) setIdxField(mv, "s", 1);
		for (int i = 0; i < an.inputs(); i++) setIdxField(mv, "i" + i, i + 2);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/** -> Value[] vars, int this.name
	 * @param mv
	 * @param name */
	private static void indexIO(MethodVisitor mv, String name) {
		mv.visitVarInsn(ALOAD, 2);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, T_IMPL, name, "I");
	}

	/**Value[], int -> (type)
	 * @param mv
	 * @param type */
	private static void load(MethodVisitor mv, Class<?> type) {
		mv.visitInsn(AALOAD);
		if (type == Value.class);
		else if (type == Type.class)
			mv.visitFieldInsn(GETFIELD, T_VALUE, "type", D_TYPE);
		else if (type == Value[].class)
			mv.visitFieldInsn(GETFIELD, T_VALUE, "elements", D_ELEMENTS);
		else if (type == byte[].class)
			mv.visitFieldInsn(GETFIELD, T_VALUE, "data", D_DATA);
		else if (type == long.class)
			mv.visitFieldInsn(GETFIELD, T_VALUE, "value", "J");
		else if (type == double.class) {
			mv.visitFieldInsn(GETFIELD, T_VALUE, "value", "J");
			mv.visitMethodInsn(INVOKESTATIC, T_DOUBLE, "longBitsToDouble", "(J)D", false);
		} else if (type == int.class) {
			mv.visitFieldInsn(GETFIELD, T_VALUE, "value", "J");
			mv.visitInsn(L2I);
		} else throw new IllegalArgumentException("unsupported input type: " + type);
	}

	/**Value[], int, x -> 
	 * @param mv
	 * @param m
	 * @param out */
	private static void store(MethodVisitor mv, Method m, String out) {
		Class<?> type = m.getReturnType();
		if (type == Value.class);
		else if (type == void.class)
			if (out.isEmpty()) {
				indexIO(mv, "i0");
				mv.visitInsn(AALOAD);
			} else newValue(mv, m, out, D_OF);
		else if (type == Type.class)
			mv.visitMethodInsn(INVOKESTATIC, T_VALUE, "of", D_OF, false);
		else if (type == Value[].class)
			newValue(mv, m, out, D_OFELEMENTS);
		else if (type == byte[].class)
			newValue(mv, m, out, D_OFDATA);
		else if (type == long.class)
			newValue(mv, m, out, D_OFVALUE);
		else if (type == double.class) {
			mv.visitMethodInsn(INVOKESTATIC, T_DOUBLE, "doubleToRawLongBits", "(D)J", false);
			newValue(mv, m, out, D_OFVALUE);
		} else if (type == int.class) {
			mv.visitInsn(I2L);
			newValue(mv, m, out, D_OFVALUE);
		} else throw new IllegalArgumentException("unsupported output type: " + type);
		mv.visitInsn(AASTORE);
	}

	/** x -> Value
	 * @param mv
	 * @param m
	 * @param out
	 * @param desc */
	private static void newValue(MethodVisitor mv, Method m, String out, String desc) {
		Class<?> c = m.getDeclaringClass();
		try {
			if ((~c.getDeclaredField(out).getModifiers() & (Modifier.STATIC | Modifier.PUBLIC)) != 0)
				throw new IllegalArgumentException("output type field " + out + " must be public static");
		} catch(NoSuchFieldException | SecurityException e) {
			throw new IllegalArgumentException("output type field " + out + " doesn't exist");
		}
		mv.visitFieldInsn(GETSTATIC, getInternalName(c), out, D_TYPE);
		mv.visitMethodInsn(INVOKESTATIC, T_VALUE, "of", desc, false);
	}

	private static void addEval(ClassWriter cw, Method m, Impl an) {
		Class<?>[] types = m.getParameterTypes();
		int j = 0;
		MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "eval", D_EVAL, null, EX_SIGNALERROR);
		mv.visitCode();
		Label start = new Label(), end = new Label();
		mv.visitTryCatchBlock(start, end, end, T_RUNTIMEEXCEPTION);
		mv.visitLabel(start);
		line(mv, 249);
		indexIO(mv, "o");
		if (an.useIp())
			if (types[j++] == Interpreter.class) {
				line(mv, 252);
				mv.visitVarInsn(ALOAD, 1);
			} else throw new IllegalArgumentException("wrong parameter type for interpreter");
		if (an.useScope()) {
			line(mv, 257);
			indexIO(mv, "s");
			load(mv, types[j++]);
		}
		line(mv, 262);
		for (int i = 0; i < an.inputs(); i++) {
			indexIO(mv, "i" + i);
			load(mv, types[j++]);
		}
		line(mv, 266);
		mv.visitMethodInsn(INVOKESTATIC, getInternalName(m.getDeclaringClass()), m.getName(), getMethodDescriptor(m), false);
		line(mv, 268);
		store(mv, m, an.outType());
		mv.visitInsn(RETURN);
		mv.visitLabel(end);
		line(mv, 272);
		throwBlock(mv);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private static void throwBlock(MethodVisitor mv) {
		mv.visitVarInsn(ASTORE, 1);
		mv.visitTypeInsn(NEW, T_SIGNALERROR);
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(GETFIELD, T_IMPL, "o", "I");
		mv.visitInsn(ACONST_NULL);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitMethodInsn(INVOKESPECIAL, T_SIGNALERROR, "<init>", D_NEWSIGNALERROR, false);
		mv.visitInsn(ATHROW);
	}

	private static void line(MethodVisitor mv, int i) {
		Label l = new Label();
		mv.visitLabel(l);
		mv.visitLineNumber(i, l);
	}

	/**@param module the module to init
	 * @param impl module Intrinsics class
	 * @return whether module is already fully loaded (skip regular loading) */
	public static boolean preInit(Module module, Class<?> impl) {
		if (impl != null) try {
			Method m = impl.getDeclaredMethod("preInit", Module.class);
			if ((boolean)m.invoke(null, module)) {
				linkAll(module, impl);
				return true;
			}
		} catch(NoSuchMethodException e) {
		} catch(SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static void linkAll(Module mod, Class<?> impl) {
		if (impl == null) return;
		Method init = null;
		for (Method m : impl.getDeclaredMethods()) {
			Impl an = m.getAnnotation(Impl.class);
			if (an == null) {
				if (m.isAnnotationPresent(Init.class)) init = m;
				continue;
			}
			BlockDef def = mod.blocks.get(m.getName());
			if (def == null) continue;
			if (DEBUG) System.out.printf("link: %s -> %s\n", def.name, m);
			try {
				def.assembler.setIntrinsic(linkIntrinsic(m, an));
			} catch (RuntimeException e) {
				System.err.printf("failed to link intrinsic for %s : %s\n", def, e);
			}
		}
		if (init != null) try {
			init.invoke(null, mod);
		} catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}

}
