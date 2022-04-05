package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.GlobalVar.GLOBALS;
import static cd4017be.dfc.lang.Signal.*;
import static cd4017be.dfc.lang.type.Primitive.*;
import static cd4017be.dfc.lang.type.Types.*;
import static java.lang.Double.*;
import static java.lang.Float.*;
import static java.lang.Long.*;
import java.util.*;
import java.util.function.*;

import cd4017be.dfc.compiler.IntrinsicCompilers;
import cd4017be.dfc.lang.type.*;
import cd4017be.dfc.lang.type.Function;
import cd4017be.dfc.lang.type.Type;
import cd4017be.dfc.lang.type.Vector;

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
		def("#uw", constant(UWORD));
		def("#us", constant(USHORT));
		def("#ui", constant(UINT));
		def("#ul", constant(ULONG));
		def("#w", constant(WORD));
		def("#s", constant(SHORT));
		def("#i", constant(INT));
		def("#l", constant(LONG));
		def("#f", constant(FLOAT));
		def("#d", constant(DOUBLE));
		def("#b", constant(BOOL));
		def("#t", IntrinsicEvaluators::constType);
		def("#x", IntrinsicEvaluators::declare);
		def("get", IntrinsicEvaluators::get);
		def("set", IntrinsicEvaluators::set);
		def("pack", IntrinsicEvaluators::pack);
		def("void", IntrinsicEvaluators::_void);
		def("struct", IntrinsicEvaluators::struct);
		def("vector", IntrinsicEvaluators::vector);
		def("array", IntrinsicEvaluators::array);
		def("count", IntrinsicEvaluators::count);
		def("zero", IntrinsicEvaluators::zero);
		def("type", IntrinsicEvaluators::type);
		def("funt", IntrinsicEvaluators::funt);
		def("ref", IntrinsicEvaluators::ref);
		def("load", IntrinsicEvaluators::load);
		def("store", IntrinsicEvaluators::store);
		def("call", IntrinsicEvaluators::call);
		def("main", IntrinsicEvaluators::main);
		def("def", IntrinsicEvaluators::def);
		def("swt", IntrinsicEvaluators::swt);
		def("loop", IntrinsicEvaluators::loop);
		def("add", binaryOp(IntrinsicEvaluators::add, null, Type::canArithmetic));
		def("sub", binaryOp(IntrinsicEvaluators::sub, null, Type::canArithmetic));
		def("mul", binaryOp(IntrinsicEvaluators::mul, null, Type::canArithmetic));
		def("div", binaryOp(IntrinsicEvaluators::div, null, Type::canArithmetic));
		def("mod", binaryOp(IntrinsicEvaluators::mod, null, Type::canArithmetic));
		def("neg", unaryOp(IntrinsicEvaluators::neg, null, t -> t instanceof Primitive && ((Primitive)t).signed));
		def("or", binaryOp((t, a, b) -> a | b, null, Type::canLogic));
		def("and", binaryOp((t, a, b) -> a & b, null, Type::canLogic));
		def("xor", binaryOp((t, a, b) -> a ^ b, null, Type::canLogic));
		def("not", unaryOp((t, a) -> ~a, null, Type::canLogic));
		def("eq", binaryOp(IntrinsicEvaluators::eq, BOOL, Type::canCompare));
		def("ne", binaryOp((t, a, b) -> ~eq(t, a, b), BOOL, Type::canCompare));
		def("lt", binaryOp((t, a, b) -> gt(t, b, a), BOOL, Type::canCompare));
		def("gt", binaryOp(IntrinsicEvaluators::gt, BOOL, Type::canCompare));
		def("le", binaryOp((t, a, b) -> ge(t, b, a), BOOL, Type::canCompare));
		def("ge", binaryOp(IntrinsicEvaluators::ge, BOOL, Type::canCompare));
		
		IntrinsicCompilers.define(INTRINSICS);
	}

	private static ITypeEvaluator constant(Primitive type) {
		return (file, out, node) -> {
			String arg = file.args[out];
			if (arg.isBlank()) {
				node.ret(img(type));
				return;
			}
			char c = arg.charAt(0);
			if (c == '"') {
				if (type.fp || type.bits < 8)
					throw new SignalError(out, -1, "string literal not allowed for float or bool");
				long[] val = new long[arg.length() - 1];
				int n = 0;
				for (int i = 1; i < arg.length(); i++) {
					c = arg.charAt(i);
					long v = 0;
					if (c != '\\') v = c;
					else if (++i < arg.length())
						switch(c = arg.charAt(i)) {
						case '\\': v = c; break;
						case 'n': v = '\n'; break;
						case 't': v = '\t'; break;
						case 'r': v = '\r'; break;
						default:
							for (int e = --i + (type.bits >> 2); i < e; i++) {
								c = arg.charAt(i);
								v <<= 4;
								if (c >= '0' && c <= '9') v += c - '0';
								else if (c >= 'a' && c <= 'f') v += c - 'a' + 10;
								else if (c >= 'A' && c <= 'F') v += c - 'A' + 10;
								else throw new SignalError(out, -1, c + " is not a hexadecimal digit");
							}
						}
					val[n++] = v;
				}
				if (n != val.length) val = Arrays.copyOf(val, n);
				node.ret(cst(Types.VECTOR(type, n, false), val));
				return;
			}
			int arr = c == '[' ? 1 : 0, n = 0;
			for (int p = 0; p >= 0; p = arg.indexOf(',', p + 1)) n++;
			long[] val = new long[n];
			n = 0;
			for (int i = arr, l = arg.length() - arr; i < l; i++) {
				if ((c = arg.charAt(i)) == ' ') continue;
				int q = arg.indexOf(',', i);
				if (q < 0) q = l;
				try {
					if (type == FLOAT)
						val[n++] = floatToRawIntBits(parseFloat(arg.substring(i, q)));
					else if (type == DOUBLE)
						val[n++] = doubleToRawLongBits(parseDouble(arg.substring(i, q)));
					else {
						int rad = 10;
						if (c == 'x') {
							rad = 16;
							i++;
						} else if (c == 'o') {
							rad = 8;
							i++;
						} else if (c == 'b') {
							rad = 1;
							i++;
						}
						val[n++] = type.signed
							? Long.parseLong(arg, i, q, rad)
							: Long.parseUnsignedLong(arg, i, q, rad);
						//TODO range check
					}
				} catch(NumberFormatException e) {
					throw new SignalError(out, -1, e.getMessage());
				}
				i = q;
			}
			node.ret(arr == 0 && n == 1
				? cst(type, val[0])
				: cst(VECTOR(type, n, arr == 0), val)
			);
		};
	}

	private static void constType(CircuitFile file, int out, Node node) throws SignalError {
		try {
			node.ret(img(Types.parseType(file.args[out])));
		} catch (IllegalArgumentException e) {
			throw new SignalError(out, -1, e.getMessage());
		}
	}

	private static void declare(CircuitFile file, int out, Node node) throws SignalError {
		Type type = file.eval(out, 0).type;
		if (!(type instanceof Pointer || type instanceof Function))
			throw new SignalError(out, 0, "expected Function or Pointer");
		node.ret(global(type, node, file.args[out]));
	}

	private static Signal evalIdx(CircuitFile file, int out, int in) throws SignalError {
		Signal s = file.eval(out, in);
		if (s == NULL) return null;
		if (s.hasValue() && s.type instanceof Primitive && !((Primitive)s.type).signed)
			return s;
		throw new SignalError(out, in, "expected unsigned integer value");
	}

	private static long[] parseIndices(CircuitFile file, int out, int in, boolean val)
	throws SignalError {
		try {
			String args = file.args[out];
			String[] arg = args.split(",");
			Signal idx = evalIdx(file, out, in);
			long[] idxs = new long[arg.length];
			for (int i = 0; i < arg.length; i++) {
				String s = arg[i].trim();
				long p;
				if (!s.equals("#"))
					p = Integer.parseUnsignedInt(s);
				else if (idx == null)
					throw new SignalError(out, in, "missing index value");
				else if (idx.isConst())
					p = idx.value;
				else if (!val)
					throw new SignalError(out, in, "can't dynamically index imaginary signal");
				else p = -1;
				idxs[i] = p;
			}
			return idxs;
		} catch(NumberFormatException e) {
			throw new SignalError(out, -1, e.getMessage());
		}
	}

	private static void get(CircuitFile file, int out, Node node) throws SignalError {
		Signal str = file.eval(out, 0);
		long[] idxs = parseIndices(file, out, 1, str.hasValue());
		node.data = idxs;
		try {
			for (long idx : idxs)
				str = str.type.getElement(str, idx);
		} catch(IllegalArgumentException e) {
			throw new SignalError(out, -1, e.getMessage());
		}
		node.ret(str);
	}

	private static void set(CircuitFile file, int out, Node node) throws SignalError {
		Signal str = file.eval(out, 0), val = file.eval(out, 1);
		if (!val.hasValue()) throw new SignalError(out, 1, "expected value");
		long[] idxs = parseIndices(file, out, 2, true);
		if (!str.isVar()) str = var(str.type);
		boolean struct = false;
		for (long idx : idxs) {
			Type type = str.type;
			if (type instanceof Vector) {
				if (!((Vector)type).simd) struct = true;
				else if (struct)
					throw new SignalError(out, 0, "can't set vector element in struct");
			} else if (type instanceof Struct) struct = true;
			else throw new SignalError(out, 0, "can only set struct, array or vector element");
			str = type.getElement(str, idx);
		}
		if (val.type != str.type)
			throw new SignalError(out, 1, "type mismatch");
		node.ret(str);
	}

	private static void pack(CircuitFile file, int out, Node node) throws SignalError {
		Signal a = file.eval(out, 0), b = file.eval(out, 1);
		node.ret(a == NULL ? b : b == NULL ? a : bundle(a, b));
	}

	private static void _void(CircuitFile file, int out, Node node) throws SignalError {
		if (!file.eval(out, 0).hasValue())
			throw new SignalError(out, 0, "can't evaluate imaginary");
		Signal val = file.eval(out, 1);
		if (!val.hasValue())
			throw new SignalError(out, 1, "can't evaluate imaginary");
		node.retSideff(val);
	}

	private static void buildStruct(Node node, Type type, Signal[] elem) {
		boolean cst = true, hasval = true;
		for (int i = 0; i < elem.length; i++) {
			Signal s = elem[i];
			cst &= s.isConst();
			hasval &= s.hasValue();
		}
		Signal r;
		if (cst) {
			long[] data = new long[elem.length];
			for (int i = 0; i < elem.length; i++)
				data[i] = elem[i].value;
			r = cst(type, data);
		} else r = hasval ? var(type) : img(type);
		node.ret(r);
	}

	private static Type[] types(Signal[] elem) {
		Type[] types = new Type[elem.length];
		for (int i = 0; i < elem.length; i++)
			types[i] = elem[i].type;
		return types;
	}

	private static void struct(CircuitFile file, int out, Node node) throws SignalError {
		Signal val = file.eval(out, 0);
		Signal[] elem = val.asBundle();
		buildStruct(node, STRUCT(types(elem)), elem);
	}

	private static void array(CircuitFile file, int out, Node node) throws SignalError {
		Signal count = evalIdx(file, out, 1), val = file.eval(out, 0);
		if (count == null) {
			Signal[] elem = val.asBundle();
			if (elem.length == 0)
				throw new SignalError(out, 0, "expected at least 1 element");
			Type t = elem[0].type;
			for (int i = 1; i < elem.length; i++) {
				Signal s = elem[i];
				if (s.type != t)
					throw new SignalError(out, 0, "type mismatch");
			}
			buildStruct(node, Types.VECTOR(t, elem.length, false), elem);
		} else if (val.type instanceof Bundle)
			throw new SignalError(out, 0, "expected only single type");
		else if (!count.isConst())
			throw new SignalError(out, 1, "expected constant");
		else node.ret(img(Types.VECTOR(val.type, (int)count.value, false)));
	}

	private static void vector(CircuitFile file, int out, Node node) throws SignalError {
		Signal count = evalIdx(file, out, 1), val = file.eval(out, 0);
		if (count == null) {
			Signal[] elem = val.asBundle();
			if (elem.length == 0)
				throw new SignalError(out, 0, "expected at least 1 element");
			Type t = elem[0].type;
			if (!t.canSimd())
				throw new SignalError(out, 0, "expected primitive or pointer values");
			for (int i = 1; i < elem.length; i++) {
				Signal s = elem[i];
				if (s.type != t)
					throw new SignalError(out, 0, "type mismatch");
			}
			buildStruct(node, VECTOR(t, elem.length, true), elem);
		} else if (val.type instanceof Bundle)
			throw new SignalError(out, 0, "expected only single type");
		else if (!val.type.canSimd())
			throw new SignalError(out, 0, "expected primitive or pointer type");
		else if (!count.isConst())
			throw new SignalError(out, 1, "expected constant");
		else {
			Vector type = VECTOR(val.type, (int)count.value, true);
			if (val.isConst()) {
				long[] arr = new long[type.count];
				Arrays.fill(arr, 0, arr.length, val.value);
				node.ret(cst(type, arr));
			} else node.ret(new Signal(type, val.state, 0L));
		}
	}

	private static void count(CircuitFile file, int out, Node node) throws SignalError {
		Signal in = file.eval(out, 0);
		Type type = in.type;
		int n;
		if (type instanceof Vector) n = ((Vector)type).count;
		else if (type instanceof Struct) n = ((Struct)type).elements.length;
		else if (type instanceof Function) n = ((Function)type).parTypes.length;
		else if (type instanceof Bundle) n = (int)in.value;
		else n = 1;
		node.ret(cst(UINT, n));
	}

	private static void zero(CircuitFile file, int out, Node node) throws SignalError {
		Signal in = file.eval(out, 0);
		if (in.type instanceof Bundle) {
			Signal[] elem = in.asBundle();
			Signal[] zero = new Signal[elem.length];
			for (int i = 0; i < elem.length; i++)
				zero[i] = new Signal(elem[i].type, CONST, 0L);
			node.ret(bundle(zero));
		} else node.ret(new Signal(in.type, CONST, 0L));
	}

	private static void type(CircuitFile file, int out, Node node) throws SignalError {
		Signal in = file.eval(out, 0);
		if (in.type instanceof Bundle) {
			Signal[] elem = in.asBundle();
			Signal[] img = new Signal[elem.length];
			for (int i = 0; i < elem.length; i++)
				img[i] = img(elem[i].type);
			node.ret(bundle(img));
		} else node.ret(img(in.type));
	}

	private static void funt(CircuitFile file, int out, Node node) throws SignalError {
		Type ret = file.eval(out, 0).type;
		Signal[] param = file.eval(out, 1).asBundle();
		node.ret(img(FUNCTION(ret, types(param))));
	}

	private static void ref(CircuitFile file, int out, Node node) throws SignalError {
		Signal val = file.eval(out, 0);
		Pointer type = new Pointer(0).to(val.type);
		node.ret(val.isConst() ? global(type, node, null) : val.hasValue() ? var(type) : img(type));
	}

	private static void load(CircuitFile file, int out, Node node) throws SignalError {
		Signal ptr = file.eval(out, 0);
		if (!(ptr.hasValue() && ptr.type instanceof Pointer))
			throw new SignalError(out, 0, "pointer value expected");
		node.ret(var(((Pointer)ptr.type).type));
	}

	private static void store(CircuitFile file, int out, Node node) throws SignalError {
		Signal ptr = file.eval(out, 0), val = file.eval(out, 1);
		if (!(ptr.hasValue() && ptr.type instanceof Pointer))
			throw new SignalError(out, 0, "pointer value expected");
		if (!val.hasValue())
			throw new SignalError(out, 1, "can't store imaginary data");
		Pointer type = (Pointer)ptr.type;
		if (val.type != type.type)
			throw new SignalError(out, 1, "type doesn't match pointer");
		node.retSideff(ptr);
	}

	private static void call(CircuitFile file, int out, Node node) throws SignalError {
		Signal f = file.eval(out, 0);
		if (!(f.hasValue() && f.type instanceof Function))
			throw new SignalError(out, 0, "expected function value");
		Function type = (Function)f.type;
		Signal[] par = file.eval(out, 1).asBundle();
		if (par.length != type.parTypes.length)
			throw new SignalError(out, 1, "types don't match function");
		for (int i = 0; i < par.length; i++) {
			Signal s = par[i];
			if (!s.hasValue())
				throw new SignalError(out, 1, "can't pass imaginary parameters");
			if (s.type != type.parTypes[i])
				throw new SignalError(out, 1, "types don't match function");
		}
		node.retSideff(var(type.retType));
	}

	private static void main(CircuitFile file, int out, Node node) throws SignalError {
		if (GLOBALS.size() != 0)
			throw new SignalError(out, -1, "duplicate main");
		node.direct = 0;
		Function type = FUNCTION(INT, UINT, parseType("[[W]*]*"));
		node.ret(global(type, node, "main"));
		Signal ret = file.eval(out, 0);
		if (ret.type != type.retType)
			throw new SignalError(out, 0, "signed int expected");
	}

	private static void def(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 1;
		Type t = file.eval(out, 0).type;
		if (!(t instanceof Function))
			throw new SignalError(out, 0, "expected function type");
		Function f = (Function)t;
		node.ret(global(f, node, null));
		Signal ret = file.eval(out, 1);
		if (!ret.hasValue())
			throw new SignalError(out, 1, "can't return imaginary");
		if (ret.type != f.retType)
			throw new SignalError(out, 1, "type doesn't match function");
	}

	private static void swt(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 1;
		Signal con = file.eval(out, 0);
		if (!(con.hasValue() && con.type == BOOL))
			throw new SignalError(out, 0, "expected boolean");
		if (con.isConst()) {
			node.ret(file.eval(out, con.asBool() ? 1 : 2));
			return;
		}
		Signal[] as = file.eval(out, 1).asBundle(), bs = file.eval(out, 2).asBundle();
		if (as.length != bs.length)
			throw new SignalError(out, 2, "branch types don't match");
		Signal[] rs = new Signal[as.length];
		for (int i = 0; i < as.length; i++) {
			Type type = as[i].type;
			if (type != bs[i].type)
				throw new SignalError(out, 2, "branch types don't match");
			rs[i] = var(type);
		}
		node.ret(bundle(rs));
	}

	private static void loop(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 1;
		Signal[] init = file.eval(out, 0).asBundle();
		Signal[] res = new Signal[init.length];
		for (int i = 0; i < res.length; i++) {
			Signal s = init[i];
			if (!s.hasValue())
				throw new SignalError(out, 0, "initial state can't be imaginary");
			res[i] = var(s.type);
		}
		node.ret(bundle(res));
		Signal cond = file.eval(out, 1);
		if (!(cond.hasValue() && cond.type == BOOL))
			throw new SignalError(out, 1, "expected boolean value");
		Signal[] nxt = file.eval(out, 2).asBundle();
		if (nxt.length != res.length)
			throw new SignalError(out, 2, "types don't match loop state");
		for (int i = 0; i < res.length; i++)
			if (res[i].type != nxt[i].type)
				throw new SignalError(out, 2, "types don't match loop state");
	}

	@FunctionalInterface
	private interface BiOpConst {
		long apply(Primitive type, long a, long b);
	}

	private static ITypeEvaluator binaryOp(BiOpConst op, Primitive res, Predicate<Type> in) {
		return (file, out, node) -> {
			Signal a = file.eval(out, 0), b = file.eval(out, 1);
			if (!a.hasValue())
				throw new SignalError(out, 0, "invalid imaginary operand");
			if (!b.hasValue())
				throw new SignalError(out, 1, "invalid imaginary operand");
			Type type = a.type, rtype = res == null ? type : res;
			if (type != b.type)
				throw new SignalError(out, 1, "types don't match");
			int vec = 0;
			if (type instanceof Vector) {
				Vector v = (Vector)type;
				if (!v.simd)
					throw new SignalError(out, 0, "invalid operand type");
				vec = v.count;
				type = v.element;
				if (res != null) rtype = Types.VECTOR(res, vec, true);
			}
			if (!in.test(type))
				throw new SignalError(out, 0, "invalid operand type");
			if (a.isVar() || b.isVar() || !(type instanceof Primitive))
				node.ret(var(rtype));
			else try {
				Primitive at = (Primitive)type, rt = res == null ? at : res;
				if (vec == 0) {
					node.ret(cst(rt, op.apply(at, a.value, b.value)));
					return;
				}
				long[] r = new long[vec];
				for (int i = 0; i < vec; i++)
					r[i] = castPrim(rt, op.apply(at, a.getIndex(i), b.getIndex(i)));
				node.ret(cst(rtype, r));
			} catch (RuntimeException e) {
				throw new SignalError(out, 1, e.getMessage());
			}
		};
	}

	@FunctionalInterface
	private interface UnOpConst {
		long apply(Primitive type, long a);
	}

	private static ITypeEvaluator unaryOp(UnOpConst op, Primitive res, Predicate<Type> in) {
		return (file, out, node) -> {
			Signal a = file.eval(out, 0);
			if (!a.hasValue())
				throw new SignalError(out, 0, "invalid imaginary operand");
			Type type = a.type, rtype = res == null ? type : res;
			int vec = 0;
			if (type instanceof Vector) {
				Vector v = (Vector)type;
				if (!v.simd)
					throw new SignalError(out, 0, "invalid operand type");
				vec = v.count;
				type = v.element;
				if (res != null) rtype = Types.VECTOR(res, vec, true);
			}
			if (!in.test(type))
				throw new SignalError(out, 0, "invalid operand type");
			if (a.isVar() || !(type instanceof Primitive))
				node.ret(var(rtype));
			else try {
				Primitive at = (Primitive)type, rt = res == null ? at : res;
				if (vec == 0) {
					node.ret(cst(rt, op.apply(at, a.value)));
					return;
				}
				long[] r = new long[vec];
				for (int i = 0; i < vec; i++)
					r[i] = castPrim(rt, op.apply(at, a.getIndex(i)));
				node.ret(cst(rtype, r));
			} catch (RuntimeException e) {
				throw new SignalError(out, 1, e.getMessage());
			}
		};
	}

	private static long add(Primitive type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) + intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) + longBitsToDouble(b));
		default -> a + b;
		};
	}

	private static long sub(Primitive type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) - intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) - longBitsToDouble(b));
		default -> a - b;
		};
	}

	private static long mul(Primitive type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) * intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) * longBitsToDouble(b));
		default -> a * b;
		};
	}

	private static long div(Primitive type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) / intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) / longBitsToDouble(b));
		default -> type.signed ? a / b : divideUnsigned(a, b);
		};
	}

	private static long mod(Primitive type, long a, long b) {
		return switch(type) {
		case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) % intBitsToFloat((int)b));
		case DOUBLE -> doubleToLongBits(longBitsToDouble(a) % longBitsToDouble(b));
		default -> type.signed ? a % b : remainderUnsigned(a, b);
		};
	}

	static long neg(Primitive type, long a) {
		return switch(type) {
		case FLOAT -> a ^ 0x80000000L;
		case DOUBLE -> a ^ 0x80000000_00000000L;
		default -> -a;
		};
	}

	private static long eq(Primitive type, long a, long b) {
		return switch(type) {
		case FLOAT -> intBitsToFloat((int)a) == intBitsToFloat((int)b);
		case DOUBLE -> longBitsToDouble(a) == longBitsToDouble(b);
		default -> a == b;
		} ? 1 : 0;
	}

	private static long gt(Primitive type, long a, long b) {
		return switch(type) {
		case FLOAT -> intBitsToFloat((int)a) > intBitsToFloat((int)b);
		case DOUBLE -> longBitsToDouble(a) > longBitsToDouble(b);
		default -> type.signed ? a > b : compareUnsigned(a, b) > 0;
		} ? 1 : 0;
	}

	private static long ge(Primitive type, long a, long b) {
		return switch(type) {
		case FLOAT -> intBitsToFloat((int)a) >= intBitsToFloat((int)b);
		case DOUBLE -> longBitsToDouble(a) >= longBitsToDouble(b);
		default -> type.signed ? a >= b : compareUnsigned(a, b) >= 0;
		} ? 1 : 0;
	}

}
