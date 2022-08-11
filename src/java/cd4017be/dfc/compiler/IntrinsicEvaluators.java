package cd4017be.dfc.compiler;

import static cd4017be.dfc.lang.Signal.*;
import static cd4017be.dfc.lang.type.Primitive.*;
import static cd4017be.dfc.lang.type.Types.*;
import static java.lang.Double.*;
import static java.lang.Float.*;
import static java.lang.Long.*;

import java.lang.invoke.*;
import java.util.*;
import java.util.function.*;

import cd4017be.dfc.graph.Context;
import cd4017be.dfc.graph.Node;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.type.*;
import cd4017be.dfc.lang.type.Function;
import cd4017be.dfc.lang.type.Vector;

/**
 * @author CD4017BE */
public class IntrinsicEvaluators {

	public static void register(BlockRegistry reg) {
		reg.addPlugin(MethodHandles.lookup(), IntrinsicEvaluators.class, IntrinsicCompilers.class);
	}

	static void out(Node node, Context c) throws SignalError {
		node.updateOutput(0, node.getInput(0, c), c);
	}

	static void in(Node node, Context c) throws SignalError {
		node.updateOutput(0, node.getInput(0, c), c);
	}

	@FunctionalInterface
	private interface BiOpConst {
		long apply(Primitive type, long a, long b);
	}

	private static void binaryOp(
		Node node, Context c, BiOpConst op,
		Primitive res, Predicate<Type> in
	) throws SignalError {
		Signal a = node.getInput(0, c), b = node.getInput(1, c);
		if (a == null || b == null) {
			node.updateOutput(0, null, c);
			return;
		}
		if (!a.hasValue())
			throw new SignalError(node, 1, "invalid imaginary operand");
		if (!b.hasValue())
			throw new SignalError(node, 2, "invalid imaginary operand");
		Type type = a.type, rtype = res == null ? type : res;
		if (type != b.type)
			throw new SignalError(node, 2, "types don't match");
		int vec = 0;
		if (type instanceof Vector) {
			Vector v = (Vector)type;
			if (!v.simd)
				throw new SignalError(node, 1, "invalid operand type");
			vec = v.count;
			type = v.element;
			if (res != null) rtype = Types.VECTOR(res, vec, true);
		}
		if (!in.test(type))
			throw new SignalError(node, 1, "invalid operand type");
		Signal out;
		if (a.isVar() || b.isVar() || !(type instanceof Primitive))
			out = var(rtype);
		else try {
			Primitive at = (Primitive)type, rt = res == null ? at : res;
			if (vec == 0)
				out = cst(rt, op.apply(at, a.asLong(), b.asLong()));
			else {
				long[] r = new long[vec];
				for (int i = 0; i < vec; i++)
					r[i] = castPrim(rt, op.apply(at, (Long)a.getIndex(i), (Long)b.getIndex(i)));
				out = new Signal(rtype, CONST, r);
			}
		} catch (RuntimeException e) {
			throw new SignalError(node, 0, e);
		}
		node.updateChngOutput(0, out, c);
	}

	static void add(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) + intBitsToFloat((int)b));
				case DOUBLE -> doubleToLongBits(longBitsToDouble(a) + longBitsToDouble(b));
				default -> a + b;
			}, null, Type::canArithmetic
		);
	}

	static void sub(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) - intBitsToFloat((int)b));
				case DOUBLE -> doubleToLongBits(longBitsToDouble(a) - longBitsToDouble(b));
				default -> a - b;
			}, null, Type::canArithmetic
		);
	}

	static void mul(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) * intBitsToFloat((int)b));
				case DOUBLE -> doubleToLongBits(longBitsToDouble(a) * longBitsToDouble(b));
				default -> a * b;
			}, null, Type::canArithmetic
		);
	}

	static void div(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) / intBitsToFloat((int)b));
				case DOUBLE -> doubleToLongBits(longBitsToDouble(a) / longBitsToDouble(b));
				default -> type.signed ? a / b : divideUnsigned(a, b);
			}, null, Type::canArithmetic
		);
	}

	static void mod(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> floatToRawIntBits(intBitsToFloat((int)a) % intBitsToFloat((int)b));
				case DOUBLE -> doubleToLongBits(longBitsToDouble(a) % longBitsToDouble(b));
				default -> type.signed ? a % b : remainderUnsigned(a, b);
			}, null, Type::canArithmetic
		);
	}

	static void or(Node node, Context c) throws SignalError {
		binaryOp(node, c, (type, a, b) -> a | b, null, Type::canLogic);
	}

	static void and(Node node, Context c) throws SignalError {
		binaryOp(node, c, (type, a, b) -> a & b, null, Type::canLogic);
	}

	static void xor(Node node, Context c) throws SignalError {
		binaryOp(node, c, (type, a, b) -> a ^ b, null, Type::canLogic);
	}

	static void eq(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> intBitsToFloat((int)a) == intBitsToFloat((int)b);
				case DOUBLE -> longBitsToDouble(a) == longBitsToDouble(b);
				default -> a == b;
			} ? 1 : 0,
			BOOL, Type::canCompare
		);
	}

	static void ne(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> intBitsToFloat((int)a) == intBitsToFloat((int)b);
				case DOUBLE -> longBitsToDouble(a) == longBitsToDouble(b);
				default -> a == b;
			} ? 0 : 1,
			BOOL, Type::canCompare
		);
	}

	static void lt(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> intBitsToFloat((int)a) < intBitsToFloat((int)b);
				case DOUBLE -> longBitsToDouble(a) < longBitsToDouble(b);
				default -> type.signed ? a < b : compareUnsigned(a, b) < 0;
			} ? 1 : 0,
			BOOL, Type::canCompare
		);
	}

	static void gt(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> intBitsToFloat((int)a) > intBitsToFloat((int)b);
				case DOUBLE -> longBitsToDouble(a) > longBitsToDouble(b);
				default -> type.signed ? a > b : compareUnsigned(a, b) > 0;
			} ? 1 : 0,
			BOOL, Type::canCompare
		);
	}

	static void le(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> intBitsToFloat((int)a) <= intBitsToFloat((int)b);
				case DOUBLE -> longBitsToDouble(a) <= longBitsToDouble(b);
				default -> type.signed ? a <= b : compareUnsigned(a, b) <= 0;
			} ? 1 : 0,
			BOOL, Type::canCompare
		);
	}

	static void ge(Node node, Context c) throws SignalError {
		binaryOp(node, c,
			(type, a, b) -> switch(type) {
				case FLOAT -> intBitsToFloat((int)a) >= intBitsToFloat((int)b);
				case DOUBLE -> longBitsToDouble(a) >= longBitsToDouble(b);
				default -> type.signed ? a >= b : compareUnsigned(a, b) >= 0;
			} ? 1 : 0,
			BOOL, Type::canCompare
		);
	}

	@FunctionalInterface
	private interface UnOpConst {
		long apply(Primitive type, long a);
	}

	private static void unaryOp(
		Node node, Context c, UnOpConst op,
		Primitive res, Predicate<Type> in
	) throws SignalError {
		Signal a = node.getInput(0, c);
		if (a == null) {
			node.updateOutput(0, null, c);
			return;
		}
		if (!a.hasValue())
			throw new SignalError(node, 1, "invalid imaginary operand");
		Type type = a.type, rtype = res == null ? type : res;
		int vec = 0;
		if (type instanceof Vector) {
			Vector v = (Vector)type;
			if (!v.simd)
				throw new SignalError(node, 1, "invalid operand type");
			vec = v.count;
			type = v.element;
			if (res != null) rtype = Types.VECTOR(res, vec, true);
		}
		if (!in.test(type))
			throw new SignalError(node, 1, "invalid operand type");
		Signal out;
		if (a.isVar() || !(type instanceof Primitive))
			out = var(rtype);
		else try {
			Primitive at = (Primitive)type, rt = res == null ? at : res;
			if (vec == 0)
				out = cst(rt, op.apply(at, a.asLong()));
			else {
				long[] r = new long[vec];
				for (int i = 0; i < vec; i++)
					r[i] = castPrim(rt, op.apply(at, (Long)a.getIndex(i)));
				out = new Signal(rtype, CONST, r);
			}
		} catch (RuntimeException e) {
			throw new SignalError(node, 0, e);
		}
		node.updateOutput(0, out, c);
	}

	static void neg(Node node, Context c) throws SignalError {
		unaryOp(node, c,
			(type, a) -> switch(type) {
				case FLOAT -> a ^ 0x80000000L;
				case DOUBLE -> a ^ 0x80000000_00000000L;
				default -> -a;
			}, null, t -> t instanceof Primitive && ((Primitive)t).signed
		);
	}

	static void not(Node node, Context c) throws SignalError {
		unaryOp(node, c, (type, a) -> ~a, null, Type::canLogic);
	}

	private static void constant(
		Node node, Context c, Primitive type
	) throws SignalError {
		String arg = node.arguments(1)[0];
		if (arg.isBlank()) {
			node.updateOutput(0, img(type), c);
			return;
		}
		char ch = arg.charAt(0);
		if (Character.isJavaIdentifierStart(ch)) {
			arg = c.extDef.macro(arg);
			if (arg == null)
				throw new SignalError(node, 1, "macro undefined");
			ch = arg.charAt(0);
		}
		if (ch == '"') {
			if (type.fp || type.bits < 8)
				throw new SignalError(node, 1, "string literal not allowed for float or bool");
			long[] val = new long[arg.length() - 1];
			int n = 0;
			for (int i = 1; i < arg.length(); i++) {
				ch = arg.charAt(i);
				long v = 0;
				if (ch == '"') {
					if (++i < arg.length() && (ch = arg.charAt(i)) == '0')
						val[n++] = 0;
					break;
				}
				if (ch != '\\') v = ch;
				else if (++i < arg.length())
					switch(ch = arg.charAt(i)) {
					case '\\', '"': v = ch; break;
					case 'b': v = '\b'; break;
					case 'f': v = '\f'; break;
					case 'n': v = '\n'; break;
					case 't': v = '\t'; break;
					case 'r': v = '\r'; break;
					default:
						for (int e = --i + (type.bits >> 2); i < e; i++) {
							ch = arg.charAt(i);
							v <<= 4;
							if (ch >= '0' && ch <= '9') v += ch - '0';
							else if (ch >= 'a' && ch <= 'f') v += ch - 'a' + 10;
							else if (ch >= 'A' && ch <= 'F') v += ch - 'A' + 10;
							else throw new SignalError(node, 1, ch + " is not a hexadecimal digit");
						}
					}
				val[n++] = v;
			}
			if (n != val.length) val = Arrays.copyOf(val, n);
			node.updateOutput(0, new Signal(Types.VECTOR(type, n, false), CONST, val), c);
			return;
		}
		int vec = ch == '[' ? 1 : ch == '(' ? 2 : 0, ofs = vec == 0 ? 0 : 1, n = 0;
		for (int p = 0; p >= 0; p = arg.indexOf(',', p + 1)) n++;
		long[] val = new long[n];
		n = 0;
		for (int i = ofs, l = arg.length() - ofs; i < l; i++) {
			if ((ch = arg.charAt(i)) == ' ') continue;
			int q = arg.indexOf(',', i);
			if (q < 0) q = l;
			try {
				if (type == FLOAT)
					val[n++] = floatToRawIntBits(parseFloat(arg.substring(i, q)));
				else if (type == DOUBLE)
					val[n++] = doubleToRawLongBits(parseDouble(arg.substring(i, q)));
				else {
					int rad = 10;
					if (ch == 'x') {
						rad = 16;
						i++;
					} else if (ch == 'o') {
						rad = 8;
						i++;
					} else if (ch == 'b') {
						rad = 1;
						i++;
					}
					long v = val[n++] = type.signed
						? Long.parseLong(arg, i, q, rad)
						: Long.parseUnsignedLong(arg, i, q, rad);
					if (v != Signal.castPrim(type, v))
						throw new SignalError(node, 1, "number out of range");
				}
			} catch(NumberFormatException e) {
				throw new SignalError(node, 1, e);
			}
			i = q;
		}
		node.updateOutput(0,
			vec == 0 ? cst(type, val[0])
			: new Signal(VECTOR(type, n, vec == 2), CONST, val)
		, c);
	}

	static void _uw(Node node, Context c) throws SignalError {
		constant(node, c, UWORD);
	}

	static void _us(Node node, Context c) throws SignalError {
		constant(node, c, USHORT);
	}

	static void _ui(Node node, Context c) throws SignalError {
		constant(node, c, UINT);
	}

	static void _ul(Node node, Context c) throws SignalError {
		constant(node, c, ULONG);
	}

	static void _w(Node node, Context c) throws SignalError {
		constant(node, c, WORD);
	}

	static void _s(Node node, Context c) throws SignalError {
		constant(node, c, SHORT);
	}

	static void _i(Node node, Context c) throws SignalError {
		constant(node, c, INT);
	}

	static void _l(Node node, Context c) throws SignalError {
		constant(node, c, LONG);
	}

	static void _f(Node node, Context c) throws SignalError {
		constant(node, c, FLOAT);
	}

	static void _d(Node node, Context c) throws SignalError {
		constant(node, c, DOUBLE);
	}

	static void _b(Node node, Context c) throws SignalError {
		constant(node, c, BOOL);
	}

	static void _x(Node node, Context c) throws SignalError {
		Signal t = node.getInput(0, c);
		if (t == null) {
			node.updateOutput(0, null, c);
			return;
		}
		String name = node.arguments(1)[0];
		Signal s = c.extDef.signal(name);
		if (s == null) {
			if (t == NULL) throw new SignalError(node, 2, "not defined");
			if (!(t.type instanceof Pointer || t.type instanceof Function))
				throw new SignalError(node, 1, "function or pointer type expected");
			/*c.extDef.define(name,*/ s = new Signal(t.type, CONST, null)/*)*/;
		}
		if (s.isConst()) {
			node.data = name;
			s.value = node;
		}
		node.updateChngOutput(0, s, c);
	}

	private static Signal evalIdx(Node node, Context c, int in) throws SignalError {
		Signal s = node.getInput(in, c);
		if (s == null || s == NULL ||
			s.hasValue() && s.type instanceof Primitive && !((Primitive)s.type).signed
		) return s;
		throw new SignalError(node, in + node.def.outCount, "expected unsigned integer value");
	}

	private static Signal parseIndices(
		Node node, Signal str, Signal idx,
		java.util.function.Function<Type, String> check
	) throws SignalError {
		try {
			String[] arg = node.arguments(1);
			long[] idxs = new long[arg.length];
			for (int i = 0; i < arg.length; i++) {
				Type type = str.type;
				String s = check.apply(type);
				if (s != null) throw new SignalError(node, 1, s);
				s = arg[i];
				if (s.isEmpty())
					throw new SignalError(node, node.def.ios(), "illegal empty index spec");
				char ch = s.charAt(0);
				long p;
				if (ch == '#') {
					if (idx == NULL)
						throw new SignalError(node, 2, "missing index value");
					else if (idx.isConst())
						p = idx.asLong();
					else if (!str.hasValue())
						throw new SignalError(node, 2, "can't dynamically index imaginary signal");
					else p = -1;
				} else if (ch >= '0' && ch <= '9')
					p = Integer.parseUnsignedInt(s);
				else if ((p = type.getIndex(s)) < 0)
					throw new SignalError(node, 0, "name '" + s + "' doesn't exist");
				idxs[i] = p;
				str = type.getElement(str, p);
				if (str == null) return str;
			}
			node.data = idxs;
		} catch(IllegalArgumentException | IndexOutOfBoundsException e) {
			throw new SignalError(node, 0, e);
		}
		return str;
	}

	static void get(Node node, Context c) throws SignalError {
		Signal str = node.getInput(0, c), idx = evalIdx(node, c, 1);
		if (str == null || idx == null) {
			node.updateOutput(0, null, c);
			return;
		}
		node.updateOutput(0, parseIndices(node, str, idx, t -> null), c);
	}

	static void set(Node node, Context c) throws SignalError {
		Signal str = node.getInput(0, c), idx = evalIdx(node, c, 1), val = node.getInput(2, c);
		if (str == null || idx == null || val == null) {
			node.updateOutput(0, null, c);
			return;
		}
		if (!val.hasValue()) throw new SignalError(node, 3, "expected value");
		if (!str.isVar()) str = var(str.type);
		boolean[] struct = {false};
		Signal el = parseIndices(node, str, idx, t -> {
			if (t instanceof Struct) struct[0] = true;
			else if (!(t instanceof Vector))
				return "can only set struct, array or vector element";
			else if (!((Vector)t).simd) struct[0] = true;
			else if (struct[0])
				return "can't set vector element in struct";
			return null;
		});
		if (el != null && val.type != el.type)
			throw new SignalError(node, 3, "type mismatch");
		node.updateOutput(0, str, c);
	}

	static void pack(Node node, Context c) throws SignalError {
		Signal a = node.getInput(0, c), b = node.getInput(1, c);
		if (a == null || b == null) {
			node.updateOutput(0, null, c);
			return;
		}
		Bundle parent = a.type instanceof Bundle ? (Bundle)a.type : null;
		if (parent == null && a.type != VOID)
			throw new SignalError(node, 1, "expected bundle or void");
		node.updateOutput(0, new Signal(
			new Bundle(parent, b, node.arguments(1)[0]),
			VAR, parent == null ? 1 : (int)a.value + 1
		), c);
	}

	static void pre(Node node, Context c) throws SignalError {
		Signal pre = node.getInput(0, c), val = node.getInput(1, c);
		if (pre == null || val == null) {
			node.updateOutput(0, null, c);
			return;
		}
		if (!pre.hasValue())
			throw new SignalError(node, 1, "can't evaluate imaginary");
		if (val == NULL) val = var(VOID);
		else if (!val.hasValue())
			throw new SignalError(node, 2, "can't evaluate imaginary");
		node.updateOutput(0, val, c);
	}

	static void post(Node node, Context c) throws SignalError {
		Signal val = node.getInput(0, c), post = node.getInput(1, c);
		if (val == null || post == null) {
			node.updateOutput(0, null, c);
			return;
		}
		if (val == NULL) val = var(VOID);
		else if (!val.hasValue())
			throw new SignalError(node, 1, "can't evaluate imaginary");
		if (!post.hasValue())
			throw new SignalError(node, 2, "can't evaluate imaginary");
		node.updateOutput(0, val, c);
	}

	private static Signal buildStruct(Type type, Bundle elem) {
		boolean cst = true, hasval = true;
		int l = 0;
		for (Bundle b = elem; b != null; b = b.parent) {
			Signal s = b.signal;
			cst &= s.isConst();
			hasval &= s.hasValue();
			l++;
		}
		if (!cst) return hasval ? var(type) : img(type);
		Object data;
		if (type instanceof Vector v && v.element instanceof Primitive) {
			long[] arr = new long[l];
			for (Bundle b = elem; b != null; b = b.parent)
				arr[--l] = b.signal.asLong();
			data = arr;
		} else {
			Object[] arr = new Object[l];
			for (Bundle b = elem; b != null; b = b.parent)
				arr[--l] = b.signal.value;
			data = arr;
		}
		return new Signal(type, CONST, data);
	}

	static void struct(Node node, Context c) throws SignalError {
		Signal val = node.getInput(0, c);
		if (val != null) {
			int l = val.bundleSize();
			Bundle elem = val.asBundle();
			Type[] types = new Type[l];
			String[] names = new String[l];
			int i = l;
			for(Bundle b = elem; b != null; b = b.parent) {
				Type type = b.signal.type;
				if (i != l && type.dynamic())
					throw new SignalError(node, 1, "only the last element may be dynamically sized");
				types[--i] = type;
				names[i] = b.name;
			}
			val = buildStruct(STRUCT(types, names), elem);
		}
		node.updateChngOutput(0, val, c);
	}

	static void array(Node node, Context c) throws SignalError {
		Signal count = evalIdx(node, c, 1), val = node.getInput(0, c), r;
		if (count == null || val == null) r = null;
		else if (count == NULL && val.type instanceof Bundle) {
			int l = val.bundleSize();
			if (l == 0) throw new SignalError(node, 1, "expected at least 1 element");
			Bundle elem = val.asBundle();
			Type t = elem.signal.type;
			for (Bundle b = elem; b != null; b = b.parent)
				if (b.signal.type != t)
					throw new SignalError(node, 1, "type mismatch");
			r = buildStruct(Types.VECTOR(t, l, false), elem);
		} else if (val.type instanceof Bundle)
			throw new SignalError(node, 1, "expected only single type");
		else if (count != NULL && !count.isConst())
			throw new SignalError(node, 2, "expected constant");
		else r = img(VECTOR(val.type, (int)count.asLong(), false));
		node.updateChngOutput(0, r, c);
	}

	static void vector(Node node, Context c) throws SignalError {
		Signal count = evalIdx(node, c, 1), val = node.getInput(0, c), r;
		if (count == null || val == null) r = null;
		else if (count == NULL) {
			int l = val.bundleSize();
			if (l == 0)
				throw new SignalError(node, 1, "expected at least 1 element");
			Bundle elem = val.asBundle();
			Type t = elem.signal.type;
			if (!t.canSimd())
				throw new SignalError(node, 1, "expected primitive or pointer values");
			for (Bundle b = elem; b != null; b = b.parent)
				if (b.signal.type != t)
					throw new SignalError(node, 1, "type mismatch");
			r = buildStruct(VECTOR(t, l, true), elem);
		} else if (val.type instanceof Bundle)
			throw new SignalError(node, 1, "expected only single type");
		else if (!val.type.canSimd())
			throw new SignalError(node, 1, "expected primitive or pointer type");
		else if (!count.isConst())
			throw new SignalError(node, 2, "expected constant");
		else {
			Vector type = VECTOR(val.type, (int)count.asLong(), true);
			if (!val.isConst()) r = new Signal(type, val.state, 0L);
			else if (type.element instanceof Primitive) {
				long[] arr = new long[type.count];
				Arrays.fill(arr, 0, arr.length, val.asLong());
				r = new Signal(type, CONST, arr);
			} else {
				Object[] arr = new Object[type.count];
				Arrays.fill(arr, 0, arr.length, val.asLong());
				r = new Signal(type, CONST, arr);
			}
		}
		node.updateChngOutput(0, r, c);
	}

	static void count(Node node, Context c) throws SignalError {
		Signal in = node.getInput(0, c);
		if (in != null) {
			Type type = in.type;
			int n;
			if (type instanceof Vector) n = ((Vector)type).count;
			else if (type instanceof Struct) n = ((Struct)type).elements.length;
			else if (type instanceof Function) n = ((Function)type).parTypes.length + 1;
			else if (type instanceof Bundle) n = in.bundleSize();
			else if (type instanceof Pointer) n = 1;
			else n = 0;
			in = cst(UINT, n);
		}
		node.updateChngOutput(0, in, c);
	}

	static void zero(Node node, Context c) throws SignalError {
		Signal in = node.getInput(0, c), r;
		if (in == null) r = null;
		else if (in.type instanceof Bundle) {
			Bundle elem = in.asBundle(), res = new Bundle(null, null, null), zero = res;
			int n = 0;
			for (Bundle b = elem; b != null; b = b.parent) {
				Signal s = new Signal(b.signal.type, CONST, 0L);
				zero = zero.parent = new Bundle(null, s, b.name);
				n++;
			}
			r = res.parent.toSignal(n);
		} else r = new Signal(in.type, CONST, 0L);
		node.updateChngOutput(0, r, c);
	}

	static void type(Node node, Context c) throws SignalError {
		Signal in = node.getInput(0, c), r;
		if (in == null) r = null;
		else if (in.type instanceof Bundle) {
			Bundle elem = in.asBundle(), res = new Bundle(null, null, null), img = res;
			int n = 0;
			for (Bundle b = elem; b != null; b = b.parent) {
				Signal s = new Signal(b.signal.type, IMAGE, 0L);
				img = img.parent = new Bundle(null, s, b.name);
				n++;
			}
			r = res.parent.toSignal(n);
		} else r = img(in.type);
		node.updateChngOutput(0, r, c);
	}

	static void funt(Node node, Context c) throws SignalError {
		Signal ret = node.getInput(0, c), par = node.getInput(1, c);
		if (ret == null || par == null) {
			node.updateOutput(0, null, c);
			return;
		}
		Type rett = ret.type;
		int l = par.bundleSize();
		Bundle param = par.asBundle();
		Type[] types = new Type[l];
		String[] names = new String[l];
		for(Bundle b = param; b != null; b = b.parent) {
			types[--l] = b.signal.type;
			names[l] = b.name;
		}
		node.updateChngOutput(0, img(FUNCTION(rett, types, names)), c);
	}

	static void ref(Node node, Context c) throws SignalError {
		if (node.out[0] == null) {
			Pointer type = new Pointer(0);
			node.data = null;
			node.out[0] = new Signal(type, CONST, node);
		}
		Signal val = node.getInput(0, c);
		if (val == null) return;
		if (val.type instanceof Bundle) {
			Bundle b = (Bundle)val.type;
			val = b.signal;
			if (b.parent != null)
				throw new SignalError(node, 1, "expected single element");
			if (!b.name.isBlank()) node.data = b.name;
		}
		Signal out = node.out[0];
		Pointer type = ((Pointer)out.type).to(val.type);
		if (val.isConst()) {
			node.out[0] = null;
			if (!out.isConst() || out.type != type)
				out = new Signal(type, CONST, node);
			node.updateOutput(0, out, c);
			return;
		}
		node.updateOutput(0, val.hasValue() ? var(type) : img(type), c);
	}

	static void load(Node node, Context c) throws SignalError {
		Signal ptr = node.getInput(0, c), r;
		if (ptr == null) r = null;
		else if (!(ptr.hasValue() && ptr.type instanceof Pointer))
			throw new SignalError(node, 1, "pointer value expected");
		else r = var(((Pointer)ptr.type).type);
		node.updateChngOutput(0, r, c);
	}

	static void store(Node node, Context c) throws SignalError {
		Signal ptr = node.getInput(0, c), val = node.getInput(1, c);
		if (ptr != null && val != null) {
			if (!(ptr.hasValue() && ptr.type instanceof Pointer))
				throw new SignalError(node, 1, "pointer value expected");
			if (!val.hasValue())
				throw new SignalError(node, 2, "can't store imaginary data");
			Pointer type = (Pointer)ptr.type;
			if (val.type != type.type)
				throw new SignalError(node, 2, "type doesn't match pointer");
		}
		node.updateOutput(0, ptr, c);
	}

	static void call(Node node, Context c) throws SignalError {
		Signal f = node.getInput(0, c), par = node.getInput(1, c);
		if (f == null || par == null) {
			node.updateOutput(0, null, c);
			return;
		}
		if (!(f.hasValue() && f.type instanceof Function))
			throw new SignalError(node, 1, "expected function value");
		Function type = (Function)f.type;
		int l = par.bundleSize(), pl = type.parTypes.length;
		if (l < pl || l > pl && !type.varArg)
			throw new SignalError(node, 2, "types don't match function");
		for (Bundle b = par.asBundle(); b != null; b = b.parent) {
			Signal s = b.signal;
			if (!s.hasValue())
				throw new SignalError(node, 2, "can't pass imaginary parameters");
			if (--l < pl && !s.type.canAssignTo(type.parTypes[l]))
				throw new SignalError(node, 2, "wrong type for parameter " + (l + 1));
		}
		node.updateChngOutput(0, var(type.retType), c);
	}

	static void main(Node node, Context c) throws SignalError {
		if (node.out[0] == null) {
			Function type = FUNCTION(INT,
				new Type[] {UINT, ARRAYPTR(ARRAYPTR(UWORD))},
				new String[] {"argc", "argv"}
			);
			node.data = "main";
			node.updateOutput(0, new Signal(type, CONST, node), c);
		}
		Signal ret = node.getInput(0, c);
		if (ret != null && ret.type != ((Function)node.out[0].type).retType)
			throw new SignalError(node, 1, "signed int expected");
	}

	static void def(Node node, Context c) throws SignalError {
		Signal t = node.getInput(0, c);
		if (t == null) {
			node.updateOutput(0, null, c);
			return;
		}
		String name = null;
		if (t.type instanceof Bundle) {
			Bundle b = (Bundle)t.type;
			name = b.name;
			t = b.signal;
			if (b.parent != null)
				throw new SignalError(node, 1, "expected single element");
		}
		if (!(t.type instanceof Function))
			throw new SignalError(node, 1, "expected function type");
		Function f = (Function)t.type;
		if (node.out[0] == null || node.out[0].type != t) {
			//Signal outer = CUR_FUNCTION;
			node.data = name;
			node.updateOutput(0, /*CUR_FUNCTION = */new Signal(f, CONST, node), c);
		}
		Signal ret = node.getInput(1, c);
		if (ret != null) {
			//CUR_FUNCTION = outer;
			if (!ret.hasValue())
				throw new SignalError(node, 2, "can't return imaginary");
			if (ret.type != f.retType)
				throw new SignalError(node, 2, "type doesn't match function");
		}
	}

	static void swt(Node node, Context c) throws SignalError {
		Signal con = node.getInput(0, c);
		if (con == null) {
			node.updateOutput(0, null, c);
			return;
		}
		if (!(con.hasValue() && con.type == BOOL))
			throw new SignalError(node, 1, "expected boolean");
		if (con.isConst()) {
			node.updateOutput(0, node.getInput(con.asBool() ? 1 : 2, c), c);
			return;
		}
		Signal as = node.getInput(1, c), bs = node.getInput(2, c), rs;
		if (as == null || bs == null) rs = null;
		else if (as.bundleSize() != bs.bundleSize())
			throw new SignalError(node, 0, "branch types don't match");
		else if (as.type instanceof Bundle) {
			Bundle res = new Bundle(null, null, null);
			for (
				Bundle a = as.asBundle(), b = bs.asBundle(), r = res;
				a != null; a = a.parent, b = b.parent
			) {
				Type type = a.signal.type;
				if (type != b.signal.type)
					throw new SignalError(node, 0, "branch types don't match");
				String name = a.name == null ? b.name : b.name == null ? a.name
					: a.name.equals(b.name) ? a.name : null;
				r = r.parent = new Bundle(null, var(type), name);
			}
			rs = res.parent.toSignal(as.bundleSize());
		} else if (as.type != bs.type)
			throw new SignalError(node, 0, "branch types don't match");
		else rs = var(as.type);
		node.updateChngOutput(0, rs, c);
	}

	static void loop(Node node, Context c) throws SignalError {
		Signal init = node.getInput(0, c), res;
		if (init == null) {
			node.updateOutput(0, null, c);
			return;
		}
		int l = init.bundleSize();
		if (init.type instanceof Bundle) {
			Bundle rs = new Bundle(null, null, null), r = rs;
			for (Bundle b = init.asBundle(); b != null; b = b.parent) {
				Signal s = b.signal;
				if (!s.hasValue())
					throw new SignalError(node, 1, "initial state can't be imaginary");
				r = r.parent = new Bundle(null, var(s.type), b.name);
			}
			res = rs.parent.toSignal(l);
		} else res = var(init.type);
		node.updateChngOutput(0, res, c);
		Signal cond = node.getInput(1, c), nxt = node.getInput(2, c);
		if (cond == null || nxt == null) return;
		if (!(cond.hasValue() && cond.type == BOOL))
			throw new SignalError(node, 2, "expected boolean value");
		if (nxt.bundleSize() != l)
			throw new SignalError(node, 3, "types don't match loop state");
		for (Bundle n = nxt.asBundle(), r = res.asBundle(); r != null; n = n.parent, r = r.parent)
			if (r.signal.type != n.signal.type)
				throw new SignalError(node, 3, "types don't match loop state");
	}

}
