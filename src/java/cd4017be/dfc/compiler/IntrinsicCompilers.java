package cd4017be.dfc.compiler;

import static cd4017be.dfc.lang.Signal.*;
import static cd4017be.dfc.lang.type.Primitive.LABEL;
import static cd4017be.dfc.lang.type.Primitive.UINT;
import static cd4017be.dfc.lang.type.Types.VOID;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

import java.util.Map;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.type.*;

/**
 * @author CD4017BE */
public class IntrinsicCompilers {

	/**@param defs known block types to define instructions for */
	public static void define(Map<String, BlockDef> defs) {
		BlockDef.setCompilers(defs, IntrinsicCompilers.class, lookup(), methodType(
			Instruction.class, Node.class, Instruction.class
		), dropArguments(identity(Instruction.class), 0, Node.class));
	}

	/** LLVM op-codes */
	private static final String
	DEFINE = "define $r $<v($($t $<f$)) nounwind {\n",
	RET = "  ret $1t $<v\n}\n",
	RET_VOID = "  ret void\n}\n",
	DECLARE = "declare $r $<v($<p) nounwind\n",
	PRIVATE_GLOBAL = "$v = private unnamed_addr global $<e $v\n",
	EXTERNAL_GLOBAL = "$v = external global $<t\n",
	EXTRACTVALUE = "  extractvalue $1t $<v, $($v$)\n",
	INSERTVALUE = "  insertvalue $1t $<v, $t $<v, $($v$)\n",
	EXTRACTELEMENT = "  extractelement $1t $<v, $t $v\n",
	INSERTELEMENT = "  insertelement $1t $<v, $t $v, $t $v\n",
	GETELEMENTPTR = "  getelementptr $1e, $<.$($t $<v$)\n",
	BITCAST = "  bitcast $1t $<v to $0t\n",
	ALLOCA = "  alloca $e\n",
	CALL = "  call $1r $<v($($t $<v$))\n",
	BR = "  br $1.$($t $<v$)\n",
	PHI = "  phi $t [$v, $v], [$v, $v]\n",
	LOAD = "  load $1e, $<t $<v\n",
	STORE = "  store $1t $<v, $t $<v\n";

	static Instruction _X(Node node, Instruction ins) {
		Signal s = node.out;
		return ins.add(s.type instanceof Function ? DECLARE : EXTERNAL_GLOBAL, s);
	}

	static Instruction get(Node node, Instruction ins) {
		if (!node.out.isVar()) return ins;
		Signal str = node.in(0), idx = node.in(1);
		long[] idxs = (long[])node.data;
		int j = 0, k = 0;
		Type type = str.type;
		if (type instanceof Function) {
			long i = idxs[j++] - 1;
			str = new Signal(((Function)type).parTypes[(int)i], Signal.VAR, i);
		} else if (str.type instanceof Bundle)
			str = str.getElement((int)idxs[j++]);
		for(k = j; k < idxs.length; k++)
			if (type instanceof Struct)
				type = ((Struct)type).elements[(int)idxs[k]];
			else if (type instanceof Vector && !((Vector)type).simd)
				type = ((Vector)type).element;
			else break;
		if (k > j) {
			Signal[] arg = new Signal[k - j + 1];
			arg[0] = str;
			for (int i = 1; j < k; i++, j++)
				arg[i] = cst(UINT, idxs[j]);
			str = j < idxs.length ? var(type) : node.out;
			ins = ins.add(EXTRACTVALUE, str, arg);
		}
		if (j >= idxs.length) return ins;
		if (type instanceof Vector) {
			long i = idxs[j++];
			type = ((Vector)type).element;
			Signal next = j < idxs.length ? var(type) : node.out;
			ins = ins.add(EXTRACTELEMENT, next, str, i < 0 ? idx : cst(UINT, i));
			if (j >= idxs.length) return ins;
			str = next;
		}
		type = ((Pointer)type).type;
		for (k = j; k < idxs.length - 1; k++)
			if (type instanceof Struct)
				type = ((Struct)type).elements[(int)idxs[k]];
			else if (type instanceof Vector)
				type = ((Vector)type).element;
		boolean end = !(type instanceof Vector && ((Vector)type).simd);
		Signal[] arg = new Signal[(end ? 3 : 2) + k - j];
		arg[0] = str;
		arg[1] = cst(UINT, 0);
		for (int i = 2; i < arg.length; i++, j++) {
			long i1 = idxs[j];
			arg[i] = i1 < 0 ? idx : cst(UINT, i1);
		}
		str = end ? node.out : var(new Pointer(0).to(type));
		ins = ins.add(GETELEMENTPTR, str, arg);
		if (end) return ins;
		long i = idxs[j];
		if (i >= 0) idx = cst(UINT, i);
		Signal ep = var(node.out.type);
		return ins.add(BITCAST, ep, str).add(GETELEMENTPTR, node.out, ep, idx);
	}

	static Instruction set(Node node, Instruction ins) {
		if (!node.out.isVar()) return ins;
		long[] idxs = (long[])node.data;
		Signal[] arg = new Signal[idxs.length + 2];
		arg[0] = node.in(0);
		arg[1] = node.in(1);
		for (int i = 0; i < idxs.length; i++) {
			long j = idxs[i];
			arg[i+2] = j < 0 ? node.in(2) : cst(UINT, j);
		}
		Type type = arg[0].type;
		return ins.add(
			(type instanceof Vector && ((Vector)type).simd)
				? INSERTELEMENT : INSERTVALUE,
			node.out, arg
		);
	}

	private static Instruction construct(Instruction ins, Signal out, Signal in, String op) {
		Signal[] val = in.asBundle();
		Type type = out.type;
		Signal str = img(type);
		int i = 0;
		for (int l = val.length - 1; i < l; i++) {
			Signal old = str;
			str = var(type);
			ins = ins.add(op, str, old, val[i], cst(UINT, i));
		}
		return ins.add(op, out, str, val[i], cst(UINT, i));
	}

	static Instruction struct(Node node, Instruction ins) {
		if (!node.out.isVar()) return ins;
		return construct(ins, node.out, node.in(0), INSERTVALUE);
	}

	static Instruction array(Node node, Instruction ins) {
		if (!node.out.isVar()) return ins;
		return construct(ins, node.out, node.in(0), INSERTVALUE);
	}

	static Instruction vector(Node node, Instruction ins) {
		if (!node.out.isVar()) return ins;
		Signal count = node.in(1), val = node.in(0);
		if (count.type == VOID)
			return construct(ins, node.out, val, INSERTELEMENT);
		Type type = node.out.type;
		Signal str = img(type);
		for (int l = (int)count.value, i = 0; i < l; i++) {
			Signal old = str;
			str = i == l - 1 ? node.out : var(type);
			ins = ins.add(INSERTELEMENT, str, old, val, cst(UINT, i));
		}
		return ins;
	}

	static Instruction ref(Node node, Instruction ins) throws Throwable {
		Signal out = node.out, in = node.in(0);
		if (out.isConst())
			return ins.add(PRIVATE_GLOBAL, out, in);
		return ins.add(ALLOCA, out).add(STORE, null, out, in);
	}

	static Instruction load(Node node, Instruction ins) {
		return ins.add(LOAD, node.out, node.in(0));
	}

	static Instruction store(Node node, Instruction ins) {
		return ins.add(STORE, null, node.in(0), node.in(1));
	}

	static Instruction call(Node node, Instruction ins) {
		Signal fun = node.in(0), out = node.out;
		Signal[] par = node.in(1).asBundle();
		Signal[] arg = new Signal[par.length + 1];
		arg[0] = fun;
		System.arraycopy(par, 0, arg, 1, par.length);
		return ins.add(CALL, out, par);
	}

	private static Instruction def(Instruction ins, Signal fun) {
		Type[] pt = ((Function)fun.type).parTypes;
		Signal[] par = new Signal[pt.length];
		for (int i = 0; i < par.length; i++)
			par[i] = var(pt[i]);
		return ins.add(DEFINE, fun, par).add(var(LABEL));
	}

	static Instruction main(Node node, Instruction ins) throws Throwable {
		ins = node.compIn(def(ins, node.out), 0);
		return ins.add(RET, null, node.in(0));
	}

	static Instruction def(Node node, Instruction ins) throws Throwable {
		ins = node.compIn(def(ins, node.out), 1);
		Signal ret = node.in(1);
		return ret.type == VOID
			? ins.add(RET_VOID, null)
			: ins.add(RET, null, ret);
	}

	static Instruction swt(Node node, Instruction ins) throws Throwable {
		Signal cond = node.in(0);
		if (cond.isConst()) return ins;
		Signal bt = var(LABEL), bf = var(LABEL), end = var(LABEL);
		//select branch
		ins = ins.add(BR, null, cond, bt, bf);
		//evaluate true branch
		ins = node.compIn(ins.add(bt), 1).add(BR, null, end);
		bt = ins.start.set;
		//evaluate false branch
		ins = node.compIn(ins.add(bf), 2).add(BR, null, end);
		bf = ins.start.set;
		//end switch
		ins = ins.add(end);
		Signal[] vt = node.in(1).asBundle(),
			vf = node.in(2).asBundle(),
			r = node.out.asBundle();
		for (int i = 0; i < r.length; i++)
			ins = ins.add(PHI, r[i], vt[i], bt, vf[i], bf);
		return ins;
	}

	static Instruction loop(Node node, Instruction ins) throws Throwable {
		Signal loop = var(LABEL), body = var(LABEL), end = var(LABEL);
		//enter the loop
		ins = ins.add(BR, null, loop);
		Signal start = ins.start.set;
		Instruction phi = ins = ins.add(loop); //remember insertion point for PHI instructions
		//evaluate while condition
		ins = node.compIn(ins, 1).add(BR, null, node.in(1), body, end);
		//evaluate body
		ins = node.compIn(ins.add(body), 2).add(BR, null, loop);
		body = ins.start.set;
		//now since we know where body ends, insert the PHI instructions
		Signal[] out = node.out.asBundle(),
			init = node.in(0).asBundle(),
			state = node.in(2).asBundle();
		Instruction next = phi.next;
		for (int i = 0; i < out.length; i++)
			phi = phi.add(PHI, out[i], init[i], start, state[i], body);
		phi.next = next;
		//exit loop
		return ins.add(end);
	}

	private static Instruction vec2(
		Node node, Instruction ins,
		String sop, String uop, String fop
	) {
		Signal a = node.in(0), b = node.in(1), o = node.out;
		String op = uop;
		if (a.type instanceof Primitive) {
			Primitive p = (Primitive)a.type;
			if (p.fp) op = fop;
			else if (p.signed) op = sop;
		};
		return ins.add("  " + op + " $1t $<v, $v\n", o, a, b);
	}

	static Instruction add(Node node, Instruction ins) {
		return vec2(node, ins, "add", "add", "fadd");
	}

	static Instruction sub(Node node, Instruction ins) {
		return vec2(node, ins, "sub", "sub", "fsub");
	}

	static Instruction mul(Node node, Instruction ins) {
		return vec2(node, ins, "mul", "mul", "fmul");
	}

	static Instruction div(Node node, Instruction ins) {
		return vec2(node, ins, "sdiv", "udiv", "fdiv");
	}

	static Instruction mod(Node node, Instruction ins) {
		return vec2(node, ins, "srem", "urem", "frem");
	}

	static Instruction or(Node node, Instruction ins) {
		return vec2(node, ins, "or", "or", null);
	}

	static Instruction and(Node node, Instruction ins) {
		return vec2(node, ins, "and", "and", null);
	}

	static Instruction xor(Node node, Instruction ins) {
		return vec2(node, ins, "xor", "xor", null);
	}

	static Instruction eq(Node node, Instruction ins) {
		return vec2(node, ins, "icmp eq", "icmp eq", "fcmp oeq");
	}

	static Instruction ne(Node node, Instruction ins) {
		return vec2(node, ins, "icmp ne", "icmp ne", "fcmp une");
	}

	static Instruction lt(Node node, Instruction ins) {
		return vec2(node, ins, "icmp slt", "icmp ult", "fcmp olt");
	}

	static Instruction gt(Node node, Instruction ins) {
		return vec2(node, ins, "icmp sgt", "icmp ugt", "fcmp ogt");
	}

	static Instruction le(Node node, Instruction ins) {
		return vec2(node, ins, "icmp sle", "icmp ule", "fcmp ole");
	}

	static Instruction ge(Node node, Instruction ins) {
		return vec2(node, ins, "icmp sge", "icmp uge", "fcmp oge");
	}

}
