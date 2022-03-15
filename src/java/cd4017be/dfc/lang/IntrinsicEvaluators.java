package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.GlobalVar.GLOBALS;
import static cd4017be.dfc.lang.Signal.*;
import static cd4017be.dfc.lang.Signal.union;
import static cd4017be.dfc.lang.Type.*;
import static cd4017be.dfc.lang.Type.TYPE;
import static java.lang.Double.doubleToLongBits;
import static java.lang.Double.longBitsToDouble;
import static java.lang.Float.floatToRawIntBits;
import static java.lang.Float.intBitsToFloat;
import static java.lang.Long.*;
import static java.lang.String.format;

import java.util.HashMap;
import java.util.function.*;

import cd4017be.dfc.compiler.IntrinsicCompilers;

/**
 * @author CD4017BE */
public class IntrinsicEvaluators {

	public static final HashMap<String, BlockDef> INTRINSICS = new HashMap<>();

	private static void def(String name, ITypeEvaluator op) {
		BlockDef def = new BlockDef(name);
		def.eval = op;
		INTRINSICS.put(name, def);
	}

	static {
		def("main", IntrinsicEvaluators::main);
		//def("in", (file, out, node) -> node.ret(var(parseTypes("I[:[:B]]"))));
		//def("out", IntrinsicEvaluators::out);
		def("#N", (file, out, node) -> node.retConst(parseConstants(file.args[out], out)));
		def("#T", IntrinsicEvaluators::_T);
		def("#X", IntrinsicEvaluators::_X);
		def("pack", IntrinsicEvaluators::pack);
		def("pick", IntrinsicEvaluators::pick);
		def("count", (file, out, node) -> node.retConst(cst(INT, file.eval(out, 0).length)));
		defVec("type", true, (a, o) -> new Signal(TYPE, a.type));
		def("ptrt", IntrinsicEvaluators::ptrt);
		def("funt", IntrinsicEvaluators::funt);
		def("elt0", elt(t -> t.par));
		def("elt1", elt(t -> t.ret));
		defVec("zero", true, (a, o) -> new Signal(a.asType(), 0L));
		def("void", IntrinsicEvaluators::_void);
		defVec("add", constMap(IntrinsicEvaluators::add, UNKNOWN, NUMBER_TYPES));
		defVec("sub", constMap(IntrinsicEvaluators::sub, UNKNOWN, NUMBER_TYPES));
		defVec("mul", constMap(IntrinsicEvaluators::mul, UNKNOWN, NUMBER_TYPES));
		defVec("div", constMap(IntrinsicEvaluators::div, UNKNOWN, NUMBER_TYPES));
		defVec("mod", constMap(IntrinsicEvaluators::mod, UNKNOWN, NUMBER_TYPES));
		defVec("udiv", constMap((t, a, b) -> divideUnsigned(a, b), UNKNOWN, INTEGER_TYPES));
		defVec("umod", constMap((t, a, b) -> remainderUnsigned(a, b), UNKNOWN, INTEGER_TYPES));
		defVec("neg", false, constMap(IntrinsicEvaluators::neg, UNKNOWN, NUMBER_TYPES));
		defVec("or", constMap((t, a, b) -> a | b, UNKNOWN, LOGIC_TYPES));
		defVec("and", constMap((t, a, b) -> a & b, UNKNOWN, LOGIC_TYPES));
		defVec("xor", constMap((t, a, b) -> a ^ b, UNKNOWN, LOGIC_TYPES));
		defVec("not", false, constMap((t, a) -> ~a, UNKNOWN, LOGIC_TYPES));
		defVec("eq", constMap(IntrinsicEvaluators::eq, BOOL, COMPARABLE_TYPES));
		defVec("ne", constMap((t, a, b) -> eq(t, a, b) ^ 1, BOOL, COMPARABLE_TYPES));
		defVec("lt", constMap((t, a, b) -> gt(t, b, a), BOOL, ORDERED_TYPES));
		defVec("gt", constMap(IntrinsicEvaluators::gt, BOOL, ORDERED_TYPES));
		defVec("le", constMap((t, a, b) -> ge(t, b, a), BOOL, ORDERED_TYPES));
		defVec("ge", constMap(IntrinsicEvaluators::ge, BOOL, ORDERED_TYPES));
		defVec("ult", constMap((t, a, b) -> compareUnsigned(a, b) >>> 31, BOOL, LOGIC_TYPES));
		defVec("ugt", constMap((t, a, b) -> compareUnsigned(b, a) >>> 31, BOOL, LOGIC_TYPES));
		defVec("ule", constMap((t, a, b) -> ~compareUnsigned(b, a) >>> 31, BOOL, LOGIC_TYPES));
		defVec("uge", constMap((t, a, b) -> ~compareUnsigned(a, b) >>> 31, BOOL, LOGIC_TYPES));
		def("ref", IntrinsicEvaluators::ref);
		defVec("idx", IntrinsicEvaluators::idx);
		def("load", IntrinsicEvaluators::load);
		def("store", IntrinsicEvaluators::store);
		def("call", IntrinsicEvaluators::call);
		def("swt", IntrinsicEvaluators::swt);
		def("loop", IntrinsicEvaluators::loop);
		def("def", IntrinsicEvaluators::def);
		IntrinsicCompilers.define(INTRINSICS);
	}

	@FunctionalInterface interface UnOp {
		Signal apply(Signal a, int out) throws SignalError;
	}

	private static void defVec(String name, boolean cst, UnOp op) {
		def(name, (file, out, node) -> {
			Signal[] a = file.eval(out, 0);
			int l = a.length;
			Signal[] res = new Signal[l];
			for (int i = 0; i < l; i++)
				res[i] = op.apply(a[i], out);
			if (cst) node.retConst(res);
			else node.ret(res);
		});
	}

	@FunctionalInterface interface BiOp {
		Signal apply(Signal a, Signal b, int out) throws SignalError;
	}

	private static void defVec(String name, BiOp op) {
		def(name, (file, out, node) -> {
			Signal[] a = file.eval(out, 0), b = file.eval(out, 1);
			int la = a.length, lb = b.length, l;
			if (la == 1) l = lb;
			else if (lb == 1 || lb == la) l = la;
			else throw new SignalError(out, -1, "inkompatible signal sizes");
			Signal sa = la == 1 ? a[0] : null, sb = lb == 1 ? b[0] : null;
			Signal[] res = new Signal[l];
			for (int i = 0; i < l; i++)
				res[i] = op.apply(sa != null ? sa : a[i], sb != null ? sb : b[i], out);
			node.ret(res);
		});
	}

	private static void check(int node, int in, int t, int... types) throws SignalError {
		for (int t1 : types)
			if (t < 0 ? t1 < 0 : t >= POINTER ? t1 >= POINTER : t1 == t)
				return;
		throw new SignalError(node, in,
			format("expected %s but got %s", name(types), name(t))
		);
	}

	private static void checkAssign(int node, int in, int src, int dst) throws SignalError {
		if (!canAssign(src, dst))
			throw new SignalError(node, in,
				format("can't assign %s to %s", name(src), name(dst))
			);
	}

	@FunctionalInterface interface BiOpConst {
		long apply(int type, long a, long b);
	}

	private static BiOp constMap(BiOpConst op, int outType, int... inTypes) {
		return (a, b, out) -> {
			check(out, 0, a.type, inTypes);
			check(out, 1, b.type, inTypes);
			int t = union(a.type, b.type);
			if (t == UNKNOWN) throw new SignalError(out, -1,
				format("can't combine %s & %s", name(a.type), name(b.type))
			);
			int to = outType == UNKNOWN ? t : outType;
			if (t >= POINTER || !(a.constant() && b.constant()))
				return new Signal(to);
			try {
				return new Signal(to, op.apply(t, a.addr, b.addr));
			} catch(RuntimeException e) {
				throw new SignalError(out, -1, e.getMessage());
			}
		};
	}

	@FunctionalInterface interface UnOpConst {
		long apply(int type, long a);
	}

	private static UnOp constMap(UnOpConst op, int outType, int... inTypes) {
		return (a, out) -> {
			check(out, 0, a.type, inTypes);
			int t = a.type;
			int to = outType == UNKNOWN ? t : outType;
			if (t >= POINTER || !a.constant())
				return new Signal(to);
			try {
				return new Signal(to, op.apply(t, a.addr));
			} catch(RuntimeException e) {
				throw new SignalError(out, -1, e.getMessage());
			}
		};
	}

	static void main(CircuitFile file, int out, Node node) throws SignalError {
		if (GLOBALS.size() != 0)
			throw new SignalError(out, -1, "duplicate main");
		node.direct = 0;
		new GlobalVar(node, "main");
		Type p = type(parseTypes("(I[:[:B]]:I)")[0]);
		node.retConst(var(p.par));
		Signal[] ret = file.eval(out, 0);
		int [] sig = p.ret;
		if (sig.length != ret.length)
			throw new SignalError(out, 0, "wrong signal size");
		for (int i = 0; i < ret.length; i++)
			checkAssign(out, 0, ret[i].type, sig[i]);
	}

	static void out(CircuitFile file, int out, Node node) throws SignalError {
		file.eval(out, 0);
		node.ret(Signal.DEAD_CODE);
	}

	static void _T(CircuitFile file, int out, Node node) throws SignalError {
		try {
			node.retConst(cstTypes(parseTypes(file.args[out])));
		} catch (IllegalArgumentException e) {
			throw new SignalError(out, -1, e.getMessage());
		}
	}

	static void _X(CircuitFile file, int out, Node node) throws SignalError {
		Signal[] a = file.eval(out, 0);
		Signal[] res = new Signal[a.length];
		String name = file.args[out];
		for (int i = 0; i < a.length; i++) {
			int t = a[i].asType();
			check(out, 0, t, POINTER, -1);
			res[i] = new Signal(t, GLOBALS.size());
			new GlobalVar(node, name);
		}
		node.retConst(res);
	}

	static void pack(CircuitFile file, int out, Node node) throws SignalError {
		int l = 0;
		for (int i = 0; i < 3; i++) l += file.eval(out, i).length;
		Signal[] res = new Signal[l];
		l = 0;
		for (int i = 0; i < 3; i++) {
			Signal[] s = node.in(i);
			System.arraycopy(s, 0, res, l, s.length);
			l += s.length;
		}
		node.ret(res);
	}

	static void pick(CircuitFile file, int out, Node node) throws SignalError {
		//TODO pointer pick
		Signal[] a = file.eval(out, 0);
		String s = select(file.args[out], a.length);
		Signal[] b = new Signal[s.length()];
		for (int i = 0; i < b.length; i++)
			b[i] = a[s.charAt(i)];
		node.ret(b);
	}

	static void ptrt(CircuitFile file, int out, Node node) throws SignalError {
		int id = newId(false);
		Signal[] res = cst(TYPE, id);
		node.retConst(res);
		int[] a = types(file.eval(out, 0));
		for (int t : a)
			if (t == TYPE || t == UNKNOWN)
				throw new SignalError(out, 0, "illegal type " + name(t));
		int[] b = types(file.eval(out, 1));
		for (int t : b)
			if (t == TYPE)
				throw new SignalError(out, 1, "illegal type " + name(t));
		byte flags = HEAP;
		Signal[] f = file.eval(out, 2);
		if (f.length > 0) {
			Signal f0 = f[0];
			check(out, 2, f0.type, BOOL);
			if (!f0.constant()) throw new SignalError(out, 2, "expected constant");
			if (f0.addr != 0) flags |= READONLY;
		}
		res[0].addr = new Type(flags, a, b).define(id);
	}

	static void funt(CircuitFile file, int out, Node node) throws SignalError {
		int id = newId(true);
		Signal[] res = cst(TYPE, id);
		node.retConst(res);
		int[] a = types(file.eval(out, 0));
		for (int t : a)
			if (t == TYPE || t == UNKNOWN)
				throw new SignalError(out, 0, "illegal type " + name(t));
		int[] b = types(file.eval(out, 1));
		for (int t : b)
			if (t == TYPE || t == UNKNOWN)
				throw new SignalError(out, 1, "illegal type " + name(t));
		res[0].addr = new Type(FUNCTION, a, b).define(id) & 0xffffffffL;
	}

	static ITypeEvaluator elt(Function<Type, int[]> section) {
		return (file, out, node) -> {
			int t = file.eval1(out, 0).asType();
			check(out, 0, t, POINTER, -1);
			node.retConst(cstTypes(section.apply(type(t))));
		};
	}

	static void _void(CircuitFile file, int out, Node node) throws SignalError {
		file.eval(out, 1);
		node.ret(file.eval(out, 0));
	}

	static long add(int type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) + intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) + longBitsToDouble(b));
		default -> a + b;
		};
	}

	static long sub(int type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) - intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) - longBitsToDouble(b));
		default -> a - b;
		};
	}

	static long mul(int type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) * intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) * longBitsToDouble(b));
		default -> a * b;
		};
	}

	static long div(int type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) / intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) / longBitsToDouble(b));
		case BYTE -> (byte)a / (byte)b;
		case SHORT -> (short)a / (short)b;
		case INT -> (int)a / (int)b;
		default -> a / b;
		};
	}

	static long mod(int type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) % intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) % longBitsToDouble(b));
		case BYTE -> (byte)a % (byte)b;
		case SHORT -> (short)a % (short)b;
		case INT -> (int)a % (int)b;
		default -> a % b;
		};
	}

	static long neg(int type, long a) {
		return switch(type) {
		case FLOAT -> a ^ 0x80000000L;
		case DOUBLE -> a ^ 0x80000000_00000000L;
		default -> -a;
		};
	}

	static long eq(int type, long a, long b) {
		return switch(type) {
		case FLOAT -> intBitsToFloat((int)a) == intBitsToFloat((int)b);
		case DOUBLE -> longBitsToDouble(a) == longBitsToDouble(b);
		default -> a == b;
		} ? 1 : 0;
	}

	static long gt(int type, long a, long b) {
		return switch(type) {
		case FLOAT -> intBitsToFloat((int)a) > intBitsToFloat((int)b);
		case DOUBLE -> longBitsToDouble(a) > longBitsToDouble(b);
		case BOOL -> a < b;
		case BYTE -> (byte)a > (byte)b;
		case SHORT -> (short)a > (short)b;
		case INT, TYPE -> (int)a > (int)b;
		default -> a > b;
		} ? 1 : 0;
	}

	static long ge(int type, long a, long b) {
		return switch(type) {
		case FLOAT -> intBitsToFloat((int)a) >= intBitsToFloat((int)b);
		case DOUBLE -> longBitsToDouble(a) >= longBitsToDouble(b);
		case BOOL -> a <= b;
		case BYTE -> (byte)a >= (byte)b;
		case SHORT -> (short)a >= (short)b;
		case INT, TYPE -> (int)a >= (int)b;
		default -> a >= b;
		} ? 1 : 0;
	}

	static void ref(CircuitFile file, int out, Node node) throws SignalError {
		Signal[] type = file.eval(out, 0);
		Signal[] arg = file.eval(out, 1);
		int t, n = 0;
		if (type.length == 0)
			t = new Type(HEAP, types(arg), Type.EMPTY).define(0);
		else {
			check(out, 0, t = type[0].asType(), POINTER);
			Type p = type(t);
			int l = arg.length - p.par.length, rl = p.ret.length;
			if (l < 0 || (rl > 0 ? l % rl : l) != 0)
				throw new SignalError(out, 1, "wrong signal size");
			int j = 0;
			for (int t1 : p.par)
				checkAssign(out, 1, arg[j++].type, t1);
			if (rl > 0) {
				while(j + rl <= arg.length) {
					for (int t1 : p.ret)
						checkAssign(out, 1, arg[j++].type, t1);
					n++;
				}
			}
		}
		if(type.length > 1) {
			node.ret(var(t));
			return;
		}
		for (Signal s : arg)
			if (!s.constant()) {
				node.ret(var(t));
				return;
			}
		node.ret(cst(t, GLOBALS.size()));
		new GlobalVar(node, null).len = n;
	}

	static Signal idx(Signal p, Signal i, int out) throws SignalError {
		check(out, 0, p.type, POINTER);
		check(out, 1, i.type, LOGIC_TYPES);
		Type t = type(p.type);
		return new Signal(new Type(t.flags, t.ret, Type.EMPTY).define(0));
	}

	static void load(CircuitFile file, int out, Node node) throws SignalError {
		Signal[] ps = file.eval(out, 0);
		int l = 0, i = 0;
		for (Signal p : ps) {
			check(out, 0, p.type, POINTER);
			l += type(p.type).par.length;
		}
		Signal[] res = new Signal[l];
		for (Signal p : ps)
			for (int t : type(p.type).par)
				res[i++] = new Signal(t);
		node.ret(res);
	}

	static void store(CircuitFile file, int out, Node node) throws SignalError {
		Signal[] r = file.eval(out, 0);
		Signal[] v = file.eval(out, 1);
		int i = 0;
		for (Signal p : r) {
			check(out, 0, p.type, POINTER);
			Type t = type(p.type);
			if (t.par.length > v.length - i)
				throw new SignalError(out, 1, "wrong signal size");
			for (int et : t.par)
				checkAssign(out, 1, v[i++].type, et);
		}
		node.ret(r);
	}

	static void call(CircuitFile file, int out, Node node) throws SignalError {
		Signal[] f = file.eval(out, 0);
		int[] types = types(file.eval(out, 1));
		for (int i = 0; i < f.length; i++) {
			int ft = f[i].type;
			check(out, 0, ft, -1);
			Type t = type(ft);
			if (!canAssign(types, t.par))
				throw new SignalError(out, 1,
					format("can't assign %s to %s", name(types), name(t.par))
				);
			types = t.ret;
		}
		node.retSideff(var(types));
	}

	static void swt(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 1;
		Signal tc = file.eval1(out, 0);
		check(out, 0, tc.type, BOOL);
		node.ret(tc.constant()
			? file.eval(out, tc.addr != 0 ? 1 : 2)
			: union(file.eval(out, 1), file.eval(out, 2), out)
		);
	}

	static void loop(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 1;
		Signal[] res = var(file.eval(out, 0));
		node.retSideff(res);
		Signal tc = file.eval1(out, 1);
		check(out, 1, tc.type, BOOL);
		Signal[] nxt = file.eval(out, 2);
		if (nxt.length != res.length)
			throw new SignalError(out, 2, "wrong signal size");
		for (int i = 0; i < res.length; i++)
			checkAssign(out, 2, nxt[i].type, res[i].type);
	}

	static void def(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 1;
		int t = file.eval1(out, 0).asType();
		check(out, 0, t, -1);
		Type p = type(t);
		int[] sig = p.par;
		Signal[] res = new Signal[sig.length + 1];
		res[0] = new Signal(t, GLOBALS.size());
		new GlobalVar(node, null);
		for (int i = 1; i < res.length; i++)
			res[i] = new Signal(sig[i-1]);
		node.retConst(res);
		Signal[] ret = file.eval(out, 1);
		sig = p.ret;
		if (sig.length != ret.length)
			throw new SignalError(out, 1, "wrong signal size");
		for (int i = 0; i < ret.length; i++)
			checkAssign(out, 1, ret[i].type, sig[i]);
	}

}
