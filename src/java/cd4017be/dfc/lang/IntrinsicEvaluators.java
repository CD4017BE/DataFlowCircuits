package cd4017be.dfc.lang;

import static cd4017be.dfc.lang.GlobalVar.GLOBALS;
import static cd4017be.dfc.lang.Signal.*;
import static cd4017be.dfc.lang.type.Function.CUR_FUNCTION;
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

	private static void alias(String alias, String def) {
		INTRINSICS.put(alias, INTRINSICS.get(def));
	}

	static {
		def("out", (file, out, node) -> node.ret(file.eval(out, 0)));
		def("in", IntrinsicEvaluators::in);
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
		def("#x", IntrinsicEvaluators::include);
		def("get", IntrinsicEvaluators::get);
		def("set", IntrinsicEvaluators::set);
		def("pack", IntrinsicEvaluators::pack);
		def("pre", IntrinsicEvaluators::pre);
		def("post", IntrinsicEvaluators::post);
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
		
		//for backwards compatibility:
		alias("void", "pre");
	}

	private static void in(CircuitFile file, int out, Node node) throws SignalError {
		node.ret(NULL);
		if (file.parent == null) return;
		int in;
		try {
			in = Integer.parseInt(file.args[out]);
			Objects.checkIndex(in, file.parentNode.in.length);
		} catch(NumberFormatException | IndexOutOfBoundsException e) {
			throw new SignalError(out, -1, "invalid pin number");
		}
		node = file.parent.evalChild(file.parentOut, file.lastIn = in);
		if (node != null) file.setNode(out, node);
	}

	private static ITypeEvaluator constant(Primitive type) {
		return (file, out, node) -> {
			String arg = file.args[out];
			if (arg.isBlank()) {
				node.ret(img(type));
				return;
			}
			char c = arg.charAt(0);
			if (Character.isJavaIdentifierStart(c)) {
				arg = file.extDef.macro(arg);
				if (arg == null)
					throw new SignalError(out, -1, "macro undefined");
				c = arg.charAt(0);
			}
			if (c == '"') {
				if (type.fp || type.bits < 8)
					throw new SignalError(out, -1, "string literal not allowed for float or bool");
				long[] val = new long[arg.length() - 1];
				int n = 0;
				for (int i = 1; i < arg.length(); i++) {
					c = arg.charAt(i);
					long v = 0;
					if (c == '"') {
						if (++i < arg.length() && (c = arg.charAt(i)) == '0')
							val[n++] = 0;
						break;
					}
					if (c != '\\') v = c;
					else if (++i < arg.length())
						switch(c = arg.charAt(i)) {
						case '\\', '"': v = c; break;
						case 'b': v = '\b'; break;
						case 'f': v = '\f'; break;
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
						long v = val[n++] = type.signed
							? Long.parseLong(arg, i, q, rad)
							: Long.parseUnsignedLong(arg, i, q, rad);
						if (v != Signal.castPrim(type, v))
							throw new SignalError(out, -1, "number out of range");
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

	private static void include(CircuitFile file, int out, Node node) throws SignalError {
		String name = file.args[out];
		Signal t = file.eval(out, 0), s = file.extDef.signal(name);
		if (s == null) {
			if (t == NULL) throw new SignalError(out, -1, "not defined");
			if (!(t.type instanceof Pointer || t.type instanceof Function))
				throw new SignalError(out, 0, "function or pointer type expected");
			file.extDef.define(name, s = new Signal(t.type, CONST, 0));
		}
		if (s.isConst() && s.value <= 0) {
			GLOBALS.add(new GlobalVar(node, name));
			s.value = GLOBALS.size();
		}
		node.ret(s);
	}

	private static Signal evalIdx(CircuitFile file, int out, int in) throws SignalError {
		Signal s = file.eval(out, in);
		if (s == NULL) return null;
		if (s.hasValue() && s.type instanceof Primitive && !((Primitive)s.type).signed)
			return s;
		throw new SignalError(out, in, "expected unsigned integer value");
	}

	private static Signal parseIndices(
		CircuitFile file, int out, Node node, Signal str, int in,
		java.util.function.Function<Type, String> check
	) throws SignalError {
		try {
			String args = file.args[out];
			String[] arg = args.split("\\s*,\\s*");
			Signal idx = evalIdx(file, out, in);
			long[] idxs = new long[arg.length];
			for (int i = 0; i < arg.length; i++) {
				Type type = str.type;
				String s = check.apply(type);
				if (s != null) throw new SignalError(out, 0, s);
				s = arg[i];
				if (s.isEmpty())
					throw new SignalError(out, -1, "illegal empty index spec");
				char c = s.charAt(0);
				long p;
				if (c == '#') {
					if (idx == null)
						throw new SignalError(out, in, "missing index value");
					else if (idx.isConst())
						p = idx.value;
					else if (!str.hasValue())
						throw new SignalError(out, in, "can't dynamically index imaginary signal");
					else p = -1;
				} else if (c >= '0' && c <= '9')
					p = Integer.parseUnsignedInt(s);
				else if ((p = type.getIndex(s)) < 0)
					throw new SignalError(out, 0, "name '" + s + "' doesn't exist");
				idxs[i] = p;
				str = type.getElement(str, p);
			}
			node.data = idxs;
		} catch(IllegalArgumentException | IndexOutOfBoundsException e) {
			throw new SignalError(out, 0, e.getMessage());
		}
		return str;
	}

	private static void get(CircuitFile file, int out, Node node) throws SignalError {
		node.ret(parseIndices(file, out, node, file.eval(out, 0), 1, t -> null));
	}

	private static void set(CircuitFile file, int out, Node node) throws SignalError {
		Signal str = file.eval(out, 0), val = file.eval(out, 1);
		if (!val.hasValue()) throw new SignalError(out, 1, "expected value");
		if (!str.isVar()) str = var(str.type);
		boolean[] struct = {false};
		Signal el = parseIndices(file, out, node, str, 2, t -> {
			if (t instanceof Struct) struct[0] = true;
			else if (!(t instanceof Vector))
				return "can only set struct, array or vector element";
			else if (!((Vector)t).simd) struct[0] = true;
			else if (struct[0])
				return "can't set vector element in struct";
			return null;
		});
		if (val.type != el.type)
			throw new SignalError(out, 1, "type mismatch");
		node.ret(str);
	}

	private static void pack(CircuitFile file, int out, Node node) throws SignalError {
		Signal a = file.eval(out, 0);
		Bundle parent = a.type instanceof Bundle ? (Bundle)a.type : null;
		if (parent == null && a.type != VOID)
			throw new SignalError(out, 0, "expected bundle or void");
		node.ret(new Signal(
			new Bundle(parent, file.eval(out, 1), file.args[out]),
			VAR, parent == null ? 1 : a.value + 1
		));
	}

	private static void pre(CircuitFile file, int out, Node node) throws SignalError {
		if (!file.eval(out, 0).hasValue())
			throw new SignalError(out, 0, "can't evaluate imaginary");
		Signal val = file.eval(out, 1);
		if (val == NULL) val = var(VOID);
		else if (!val.hasValue())
			throw new SignalError(out, 1, "can't evaluate imaginary");
		node.retSideff(val);
	}

	private static void post(CircuitFile file, int out, Node node) throws SignalError {
		Signal val = file.eval(out, 0);
		if (val == NULL) val = var(VOID);
		else if (!val.hasValue())
			throw new SignalError(out, 0, "can't evaluate imaginary");
		if (!file.eval(out, 1).hasValue())
			throw new SignalError(out, 1, "can't evaluate imaginary");
		node.retSideff(val);
	}

	private static void buildStruct(Node node, Type type, Bundle elem) {
		boolean cst = true, hasval = true;
		int l = 0;
		for (Bundle b = elem; b != null; b = b.parent) {
			Signal s = b.signal;
			cst &= s.isConst();
			hasval &= s.hasValue();
			l++;
		}
		Signal r;
		if (cst) {
			long[] data = new long[l];
			for (Bundle b = elem; b != null; b = b.parent)
				data[--l] = b.signal.value;
			r = cst(type, data);
		} else r = hasval ? var(type) : img(type);
		node.ret(r);
	}

	private static void struct(CircuitFile file, int out, Node node) throws SignalError {
		Signal val = file.eval(out, 0);
		int l = val.bundleSize();
		Bundle elem = val.asBundle();
		Type[] types = new Type[l];
		String[] names = new String[l];
		int i = l;
		for(Bundle b = elem; b != null; b = b.parent) {
			Type type = b.signal.type;
			if (i != l && type.dynamic())
				throw new SignalError(out, 0, "only the last element may be dynamically sized");
			types[--i] = type;
			names[i] = b.name;
		}
		buildStruct(node, STRUCT(types, names), elem);
	}

	private static void array(CircuitFile file, int out, Node node) throws SignalError {
		Signal count = evalIdx(file, out, 1), val = file.eval(out, 0);
		if (count == null && val.type instanceof Bundle) {
			int l = val.bundleSize();
			if (l == 0) throw new SignalError(out, 0, "expected at least 1 element");
			Bundle elem = val.asBundle();
			Type t = elem.signal.type;
			for (Bundle b = elem; b != null; b = b.parent)
				if (b.signal.type != t)
					throw new SignalError(out, 0, "type mismatch");
			buildStruct(node, Types.VECTOR(t, l, false), elem);
		} else if (val.type instanceof Bundle)
			throw new SignalError(out, 0, "expected only single type");
		else if (count != null && !count.isConst())
			throw new SignalError(out, 1, "expected constant");
		else node.ret(img(Types.VECTOR(val.type, count == null ? 0 : (int)count.value, false)));
	}

	private static void vector(CircuitFile file, int out, Node node) throws SignalError {
		Signal count = evalIdx(file, out, 1), val = file.eval(out, 0);
		if (count == null) {
			int l = val.bundleSize();
			if (l == 0)
				throw new SignalError(out, 0, "expected at least 1 element");
			Bundle elem = val.asBundle();
			Type t = elem.signal.type;
			if (!t.canSimd())
				throw new SignalError(out, 0, "expected primitive or pointer values");
			for (Bundle b = elem; b != null; b = b.parent)
				if (b.signal.type != t)
					throw new SignalError(out, 0, "type mismatch");
			buildStruct(node, VECTOR(t, l, true), elem);
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
		else if (type instanceof Function) n = ((Function)type).parTypes.length + 1;
		else if (type instanceof Bundle) n = in.bundleSize();
		else if (type instanceof Pointer) n = 1;
		else n = 0;
		node.ret(cst(UINT, n));
	}

	private static void zero(CircuitFile file, int out, Node node) throws SignalError {
		Signal in = file.eval(out, 0);
		if (in.type instanceof Bundle) {
			Bundle elem = in.asBundle(), res = new Bundle(null, null, null), zero = res;
			int n = 0;
			for (Bundle b = elem; b != null; b = b.parent) {
				Signal s = new Signal(b.signal.type, CONST, 0L);
				zero = zero.parent = new Bundle(null, s, b.name);
				n++;
			}
			node.ret(res.parent.toSignal(n));
		} else node.ret(new Signal(in.type, CONST, 0L));
	}

	private static void type(CircuitFile file, int out, Node node) throws SignalError {
		Signal in = file.eval(out, 0);
		if (in.type instanceof Bundle) {
			Bundle elem = in.asBundle(), res = new Bundle(null, null, null), img = res;
			int n = 0;
			for (Bundle b = elem; b != null; b = b.parent) {
				Signal s = new Signal(b.signal.type, IMAGE, 0L);
				img = img.parent = new Bundle(null, s, b.name);
				n++;
			}
			node.ret(res.parent.toSignal(n));
		} else node.ret(img(in.type));
	}

	private static void funt(CircuitFile file, int out, Node node) throws SignalError {
		Type ret = file.eval(out, 0).type;
		Signal par = file.eval(out, 1);
		int l = par.bundleSize();
		Bundle param = par.asBundle();
		Type[] types = new Type[l];
		String[] names = new String[l];
		for(Bundle b = param; b != null; b = b.parent) {
			types[--l] = b.signal.type;
			names[l] = b.name;
		}
		node.ret(img(FUNCTION(ret, types, names)));
	}

	private static void ref(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 0;
		Pointer type = new Pointer(0);
		GLOBALS.add(null);
		int i = GLOBALS.size();
		node.ret(new Signal(type, CONST, i));
		Signal val = file.eval(out, 0);
		String name = null;
		if (val.type instanceof Bundle) {
			Bundle b = (Bundle)val.type;
			val = b.signal;
			if (b.parent != null)
				throw new SignalError(out, 0, "expected single element");
			if (!b.name.isBlank()) name = b.name;
		}
		type = type.to(val.type);
		if (val.isConst()) {
			GLOBALS.set(i - 1, new GlobalVar(node, name));
			return;
		} else if (GLOBALS.size() == node.out.value)
			GLOBALS.remove(i - 1);
		node.ret(val.hasValue() ? var(type) : img(type));
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
		Signal par = file.eval(out, 1);
		int l = par.bundleSize(), pl = type.parTypes.length;
		if (l < pl || l > pl && !type.varArg)
			throw new SignalError(out, 1, "types don't match function");
		for (Bundle b = par.asBundle(); b != null; b = b.parent) {
			Signal s = b.signal;
			if (!s.hasValue())
				throw new SignalError(out, 1, "can't pass imaginary parameters");
			if (--l < pl && !s.type.canAssignTo(type.parTypes[l]))
				throw new SignalError(out, 1, "wrong type for parameter " + (l + 1));
		}
		node.retSideff(var(type.retType));
	}

	private static void main(CircuitFile file, int out, Node node) throws SignalError {
		if (GLOBALS.size() != 0 || CUR_FUNCTION != null)
			throw new SignalError(out, -1, "duplicate main");
		node.direct = 0;
		Function type = FUNCTION(INT,
			new Type[] {UINT, ARRAYPTR(ARRAYPTR(UWORD))},
			new String[] {"argc", "argv"}
		);
		node.ret(CUR_FUNCTION = global(type, node, "main"));
		Signal ret = file.eval(out, 0);
		CUR_FUNCTION = null;
		if (ret.type != type.retType)
			throw new SignalError(out, 0, "signed int expected");
	}

	private static void def(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 1;
		Signal t = file.eval(out, 0);
		String name = null;
		if (t.type instanceof Bundle) {
			Bundle b = (Bundle)t.type;
			name = b.name;
			t = b.signal;
			if (b.parent != null)
				throw new SignalError(out, 0, "expected single element");
		}
		if (!(t.type instanceof Function))
			throw new SignalError(out, 0, "expected function type");
		Function f = (Function)t.type;
		Signal outer = CUR_FUNCTION;
		node.ret(CUR_FUNCTION = global(f, node, name));
		Signal ret = file.eval(out, 1);
		CUR_FUNCTION = outer;
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
		Signal as = file.eval(out, 1), bs = file.eval(out, 2), rs;
		int l = as.bundleSize();
		if (l != bs.bundleSize())
			throw new SignalError(out, 2, "branch types don't match");
		if (as.type instanceof Bundle) {
			Bundle res = new Bundle(null, null, null);
			for (
				Bundle a = as.asBundle(), b = bs.asBundle(), r = res;
				a != null; a = a.parent, b = b.parent
			) {
				Type type = a.signal.type;
				if (type != b.signal.type)
					throw new SignalError(out, 2, "branch types don't match");
				String name = a.name == null ? b.name : b.name == null ? a.name
					: a.name.equals(b.name) ? a.name : null;
				r = r.parent = new Bundle(null, var(type), name);
			}
			rs = res.parent.toSignal(l);
		} else if (as.type != bs.type)
			throw new SignalError(out, 2, "branch types don't match");
		else rs = var(as.type);
		node.ret(rs);
	}

	private static void loop(CircuitFile file, int out, Node node) throws SignalError {
		node.direct = 1;
		Signal init = file.eval(out, 0);
		int l = init.bundleSize();
		Signal res;
		if (init.type instanceof Bundle) {
			Bundle rs = new Bundle(null, null, null), r = rs;
			for (Bundle b = init.asBundle(); b != null; b = b.parent) {
				Signal s = b.signal;
				if (!s.hasValue())
					throw new SignalError(out, 0, "initial state can't be imaginary");
				r = r.parent = new Bundle(null, var(s.type), b.name);
			}
			res = rs.parent.toSignal(l);
		} else res = var(init.type);
		node.ret(res);
		Signal cond = file.eval(out, 1);
		if (!(cond.hasValue() && cond.type == BOOL))
			throw new SignalError(out, 1, "expected boolean value");
		Signal nxt = file.eval(out, 2);
		if (nxt.bundleSize() != l)
			throw new SignalError(out, 2, "types don't match loop state");
		for (Bundle n = nxt.asBundle(), r = res.asBundle(); r != null; n = n.parent, r = r.parent)
			if (r.signal.type != n.signal.type)
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
