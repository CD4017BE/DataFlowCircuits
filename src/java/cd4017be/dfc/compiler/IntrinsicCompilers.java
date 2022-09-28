package cd4017be.dfc.compiler;

import static cd4017be.dfc.lang.Signal.*;
import static cd4017be.dfc.lang.type.Primitive.LABEL;
import static cd4017be.dfc.lang.type.Primitive.UINT;
import static cd4017be.dfc.lang.type.Types.VOID;
import cd4017be.dfc.graph.Node;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.type.*;

/**
 * @author CD4017BE */
public class IntrinsicCompilers {

	/** LLVM op-codes */
	private static final String
	DEFINE = "\ndefine $r $<v($($t $<f$)) nounwind {\n",
	RET = " ret $1t $<v\n}\n",
	RET_VOID = " ret void\n}\n",
	DECLARE = "declare $r $<v($<p) nounwind\n",
	PRIVATE_GLOBAL = "$v = private unnamed_addr global $<e $v\n",
	EXTERNAL_GLOBAL = "$v = external global $<t\n",
	EXTRACTVALUE = " extractvalue $1t $<v, $($v$)\n",
	INSERTVALUE = " insertvalue $1t $<v, $t $<v, $($v$)\n",
	EXTRACTELEMENT = " extractelement $1t $<v, $t $v\n",
	INSERTELEMENT = " insertelement $1t $<v, $t $v, $t $v\n",
	GETELEMENTPTR = " getelementptr $1e, $<.$($t $<v$)\n",
	BITCAST = " bitcast $1t $<v to $0t\n",
	TRUNC = " trunc $1t $<v to $0t\n",
	ZEXT = " zext $1t $<v to $0t\n",
	SEXT = " sext $1t $<v to $0t\n",
	FPTRUNC = " fptrunc $1t $<v to $0t\n",
	FPEXT = " fpext $1t $<v to $0t\n",
	FPTOSI = " fptosi $1t $<v to $0t\n",
	FPTOUI = " fptoui $1t $<v to $0t\n",
	SITOFP = " sitofp $1t $<v to $0t\n",
	UITOFP = " uitofp $1t $<v to $0t\n",
	INTTOPTR = " inttoptr $1t $<v to $0t\n",
	PTRTOINT = " ptrtoint $1t $<v to $0t\n",
	ALLOCA = " alloca $e\n",
	CALL = " call $1e $<v($($t $<v$))\n",
	BR = " br $1.$($t $<v$)\n",
	PHI = " phi $t [$v, $v], [$v, $v]\n",
	LOAD = " load $1e, $<t $<v\n",
	STORE = " store $1t $<v, $t $<v\n";

	static void out(NodeInstruction ni, Compiler c) {
		new NodeInstruction(ni, ni.node.input(0), ni.after);
	}

	static void in(NodeInstruction ni, Compiler c) {
		new NodeInstruction(ni, ni.node.input(0), ni.after);
	}

	static void _x(NodeInstruction ni, Compiler c) {
		Signal s = ni.out(0);
		c.addGlobal(s.type instanceof Function ? DECLARE : EXTERNAL_GLOBAL, false, s);
	}

	static void get(NodeInstruction ni, Compiler c) {
		Signal o = ni.out(0);
		if (ni.evalConst(o)) return;
		Signal str = ni.in(0), idx = ni.in(1);
		Instruction ins = ni.evalIns(0, 1).after;
		long[] idxs = ni.data();
		int j = 0, k = 0;
		while(str.type instanceof Bundle) str = str.getElement((int)idxs[j++]);
		if (str.type instanceof Function) {
			long i = idxs[j++] - 1;
			str = new Signal(((Function)str.type).parTypes[(int)i], Signal.VAR, i);
		}
		Type type = str.type;
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
			str = j < idxs.length ? var(type) : o;
			ins = ins.add(EXTRACTVALUE, str, arg);
		}
		if (j >= idxs.length) return;
		if (type instanceof Vector) {
			long i = idxs[j++];
			type = ((Vector)type).element;
			Signal next = j < idxs.length ? var(type) : o;
			ins = ins.add(EXTRACTELEMENT, next, str, i < 0 ? idx : cst(UINT, i));
			if (j >= idxs.length) return;
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
		str = end ? o : var(new Pointer(0).to(type));
		ins = ins.add(GETELEMENTPTR, str, arg);
		if (end) return;
		long i = idxs[j];
		if (i >= 0) idx = cst(UINT, i);
		Signal ep = var(o.type);
		ins.add(BITCAST, ep, str).add(GETELEMENTPTR, o, ep, idx);
	}

	static void set(NodeInstruction ni, Compiler c) {
		Signal o = ni.out(0);
		if (ni.evalConst(o)) return;
		long[] idxs = ni.data();
		Signal[] arg = new Signal[idxs.length + 2];
		arg[0] = ni.in(0);
		arg[1] = ni.in(1);
		for (int i = 0; i < idxs.length; i++) {
			long j = idxs[i];
			arg[i+2] = j < 0 ? ni.in(2) : cst(UINT, j);
		}
		Type type = arg[0].type;
		ni.evalIns(0, 1, 2).after.add(
			(type instanceof Vector && ((Vector)type).simd)
				? INSERTELEMENT : INSERTVALUE,
			o, arg
		);
	}

	static void cast(NodeInstruction ni, Compiler c) throws CompileError {
		Signal v = ni.in(0), o = ni.out(0);
		if (ni.evalConst(o)) return;
		Instruction ins = ni.evalIns(0).after;
		Type t0 = v.type, t1 = o.type;
		String op;
		if (t0 instanceof Primitive p0)
			if (t1 instanceof Primitive p1)
				op = p0.fp ?
						p1.fp ?
							p0.bits < p1.bits ? FPEXT
							: p0.bits > p1.bits ? FPTRUNC
							: BITCAST
						: p1.signed ? FPTOSI
						: FPTOUI
					: p1.fp ?
						p0.signed ? SITOFP
						: UITOFP
					: p0.bits < p1.bits ?
						p0.signed ? SEXT
						: ZEXT
					: p0.bits > p1.bits ? TRUNC
					: BITCAST;
			else if (t1 instanceof Pointer p1)
				op = INTTOPTR;
			else throw new CompileError(ni.node, "invalid cast type");
		else if (t0 instanceof Pointer p0)
			if (t1 instanceof Primitive)
				op = PTRTOINT;
			else if (t1 instanceof Pointer)
				op = BITCAST;
			else throw new CompileError(ni.node, "invalid cast type");
		else throw new CompileError(ni.node, "invalid cast type");
		ins.add(op, o, v);
	}

	static void pack(NodeInstruction ni, Compiler c) {
		ni.evalIns(0, 1);
	}

	static void pre(NodeInstruction ni, Compiler c) {
		ni.evalIns(0, 1);
	}

	static void post(NodeInstruction ni, Compiler c) {
		ni.evalIns(0, 1);
	}

	private static Instruction construct(Instruction ins, Signal out, Signal in, String op) {
		Bundle val = in.asBundle();
		Type type = out.type;
		Signal str = img(type);
		for (int i = in.bundleSize(); --i > 0; val = val.parent) {
			Signal old = str;
			str = var(type);
			ins = ins.add(op, str, old, val.signal, cst(UINT, i));
		}
		return ins.add(op, out, str, val.signal, cst(UINT, 0));
	}

	static void struct(NodeInstruction ni, Compiler c) {
		Signal o = ni.out(0);
		if (ni.evalConst(o)) return;
		construct(ni.evalIns(0).after, o, ni.in(0), INSERTVALUE);
	}

	static void array(NodeInstruction ni, Compiler c) {
		Signal o = ni.out(0);
		if (ni.evalConst(o)) return;
		construct(ni.evalIns(0).after, o, ni.in(0), INSERTVALUE);
	}

	static void vector(NodeInstruction ni, Compiler c) {
		Signal o = ni.out(0);
		if (ni.evalConst(o)) return;
		Signal count = ni.in(1), val = ni.in(0);
		if (count.type == VOID) {
			construct(ni.evalIns(0).after, o, val, INSERTELEMENT);
			return;
		}
		Instruction ins = ni.evalIns(0, 1).after;
		Signal str = img(o.type);
		for (int l = (int)count.asLong(), i = 0; i < l; i++) {
			Signal old = str;
			str = i == l - 1 ? o : var(o.type);
			ins = ins.add(INSERTELEMENT, str, old, val, cst(UINT, i));
		}
	}

	static void ref(NodeInstruction ni, Compiler c) {
		Signal out = ni.out(0), in = ni.in(0);
		if (out.isConst()) {
			ni.evalConst(in);
			c.addGlobal(PRIVATE_GLOBAL, false, out, in);
		} else ni.evalIns(0).after.add(ALLOCA, out).add(STORE, null, out, in);
	}

	static void load(NodeInstruction ni, Compiler c) {
		ni.evalIns(0).after.add(LOAD, ni.out(1), ni.out(0));
	}

	static void store(NodeInstruction ni, Compiler c) {
		ni.evalIns(0, 1).after.add(STORE, null, ni.in(1), ni.in(0));
	}

	static void call(NodeInstruction ni, Compiler c) {
		Signal par = ni.in(1);
		int l = par.bundleSize() + 1;
		Signal[] arg = new Signal[l];
		for (Bundle b = par.asBundle(); b != null; b = b.parent)
			arg[--l] = b.signal;
		arg[0] = ni.in(0);
		Signal out = ni.out(0);
		ni.evalIns(0, 1).after.add(CALL, out.type == VOID ? null : out, arg);
	}

	private static void defFunction(NodeInstruction ni, Compiler c, int retIn) {
		Signal fun = ni.out(0);
		Type[] pt = ((Function)fun.type).parTypes;
		Signal[] par = new Signal[pt.length];
		for (int i = 0; i < par.length; i++)
			par[i] = var(pt[i]);
		Instruction ins = c.addGlobal(DEFINE, true, fun, par);
		ins = new NodeInstruction(ni, ni.node.input(retIn), ins).after;
		Signal ret = ni.in(retIn);
		if (ret.type == VOID)
			ins.add(RET_VOID, null);
		else ins.add(RET, null, ret);
	}

	static void main(NodeInstruction ni, Compiler c) {
		defFunction(ni, c, 0);
	}

	static void def(NodeInstruction ni, Compiler c) {
		defFunction(ni, c, 1);
	}

	static void swt(NodeInstruction ni, Compiler c) {
		Signal cond = ni.in(0);
		if (cond.isConst()) {
			ni.evalIns(1, 2);
			return;
		}
		Bundle vt = ni.in(1).asBundle(), vf = ni.in(2).asBundle(), r = ni.out(0).asBundle();
		Node node = ni.node;
		Instruction ins = (ni = ni.evalIns(0)).after;
		Signal bt = var(LABEL), bf = var(LABEL), end = var(LABEL);
		//select branch
		ins = ins.addBr(BR, null, cond, bt, bf);
		//evaluate true branch
		ni = new NodeInstruction(ni, node.input(1), ins.add(bt));
		ins = ni.after.add(bt = var(LABEL)).addBr(BR, null, end);
		//evaluate false branch
		ni = new NodeInstruction(ni, node.input(2), ins.add(bf));
		ins = ni.after.add(bf = var(LABEL)).addBr(BR, null, end);
		//end switch
		ins = ins.add(end);
		for (; r != null; r = r.parent, vt = vt.parent, vf = vf.parent)
			ins = ins.add(PHI, r.signal, vt.signal, bt, vf.signal, bf);
	}

	static void loop(NodeInstruction ni, Compiler c) {
		Bundle out = ni.out(0).asBundle(), init = ni.in(0).asBundle(), state = ni.in(2).asBundle();
		Node node = ni.node;
		Instruction ins = (ni = ni.evalIns(0)).after;
		Signal loop = var(LABEL), body = var(LABEL), end = var(LABEL), start = var(LABEL);
		//enter the loop
		Instruction phi = ins = ins.add(start).addBr(BR, null, loop);
		ins = ins.add(loop);
		//evaluate while condition
		ni = new NodeInstruction(ni, node.input(1), ins);
		ins = ni.after.addBr(BR, null, node.input(1).signal(), body, end);
		//evaluate body
		ni = new NodeInstruction(ni, node.input(2), ins.add(body));
		ins = ni.after.add(body = var(LABEL)).addBr(BR, null, loop);
		//now since we know where body ends, insert the PHI instructions
		Instruction next = phi.next;
		for (; out != null; out = out.parent, init = init.parent, state = state.parent)
			phi = phi.add(PHI, out.signal, init.signal, start, state.signal, body);
		phi.next = next;
		//exit loop
		ins.add(end);
	}

	private static void vec2(
		NodeInstruction ni,
		String sop, String uop, String fop
	) {
		Signal o = ni.out(0);
		if (ni.evalConst(o)) return;
		Signal a = ni.in(0), b = ni.in(1);
		String op = a.type instanceof Primitive p
			? p.fp ? fop : p.signed ? sop : uop
			: uop;
		ni.evalIns(0, 1).after.add(" " + op + " $1t $<v, $v\n", o, a, b);
	}

	static void add(NodeInstruction ni, Compiler c) {
		vec2(ni, "add", "add", "fadd");
	}

	static void sub(NodeInstruction ni, Compiler c) {
		vec2(ni, "sub", "sub", "fsub");
	}

	static void mul(NodeInstruction ni, Compiler c) {
		vec2(ni, "mul", "mul", "fmul");
	}

	static void div(NodeInstruction ni, Compiler c) {
		vec2(ni, "sdiv", "udiv", "fdiv");
	}

	static void mod(NodeInstruction ni, Compiler c) {
		vec2(ni, "srem", "urem", "frem");
	}

	static void bsl(NodeInstruction ni, Compiler c) {
		vec2(ni, "shl", "shl", null);
	}

	static void bsr(NodeInstruction ni, Compiler c) {
		vec2(ni, "ashr", "lshr", null);
	}

	static void or(NodeInstruction ni, Compiler c) {
		vec2(ni, "or", "or", null);
	}

	static void and(NodeInstruction ni, Compiler c) {
		vec2(ni, "and", "and", null);
	}

	static void xor(NodeInstruction ni, Compiler c) {
		vec2(ni, "xor", "xor", null);
	}

	static void eq(NodeInstruction ni, Compiler c) {
		vec2(ni, "icmp eq", "icmp eq", "fcmp oeq");
	}

	static void ne(NodeInstruction ni, Compiler c) {
		vec2(ni, "icmp ne", "icmp ne", "fcmp une");
	}

	static void lt(NodeInstruction ni, Compiler c) {
		vec2(ni, "icmp slt", "icmp ult", "fcmp olt");
	}

	static void gt(NodeInstruction ni, Compiler c) {
		vec2(ni, "icmp sgt", "icmp ugt", "fcmp ogt");
	}

	static void le(NodeInstruction ni, Compiler c) {
		vec2(ni, "icmp sle", "icmp ule", "fcmp ole");
	}

	static void ge(NodeInstruction ni, Compiler c) {
		vec2(ni, "icmp sge", "icmp uge", "fcmp oge");
	}

	static void neg(NodeInstruction ni, Compiler c) {
		Signal a = ni.in(0), o = ni.out(0);
		if (ni.evalConst(o)) return;
		ni.evalIns(0).after.add(
			((Primitive)a.type).fp ? " fneg $1t $<v\n" : " sub $1t 0, $<v\n",
			o, a
		);
	}

	static void not(NodeInstruction ni, Compiler c) {
		Signal o = ni.out(0);
		if (ni.evalConst(o)) return;
		ni.evalIns(0).after.add(" xor $1t -1, $<v\n", o, ni.in(0));
	}

}
