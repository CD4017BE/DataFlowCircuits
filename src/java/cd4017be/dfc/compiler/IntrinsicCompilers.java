package cd4017be.dfc.compiler;

import static cd4017be.dfc.lang.Signal.types;
import static cd4017be.dfc.lang.Type.*;
import static java.lang.Math.min;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

import java.util.Map;
import cd4017be.dfc.lang.*;

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
	EXTERNAL_GLOBAL = "$v = external global $<t\n",
	EXTRACTVALUE = "  extractvalue $1e $<v, $v\n",
	INSERTVALUE = "  insertvalue $e $v, $t $<v, $v\n",
	GETELEMENTPTR = "  getelementptr $1e, $<.$($t $<v$)\n",
	BITCAST = "  bitcast $1t $<v to $0t\n",
	ALLOCA = "  alloca $e\n",
	CALL = "  call $1r $<v($($t $<v$))\n",
	BR = "  br $1.$($t $<v$)\n",
	PHI = "  phi $t [$v, $v], [$v, $v]\n",
	LOAD = "  load $1e, $<t $<v\n",
	STORE = "  store $1t $<v, $t $<v\n";

	static Instruction main(Node node, Instruction ins) throws Throwable {
		Signal[] par = node.out, ret = node.in(0);
		Signal f = new Signal(new Type(FUNCTION, types(par), types(ret)).define(0), 1L);
		ins = ins.add(DEFINE, f, par).add(new Signal(LABEL));
		ins = node.compIn(ins, 0);
		return ins.add(ret.length == 0 ? RET_VOID : RET, null, ret);
	}

	static Instruction _X(Node node, Instruction ins) {
		for (Signal s : node.out)
			ins = ins.add(s.type < 0 ? DECLARE : EXTERNAL_GLOBAL, s);
		return ins;
	}
/*
	static Instruction in(Node node, Instruction ins) {
		int i = 0;
		for (Signal s : node.out) s.define(i++);
		return ins;
	}

	static Instruction out(Node node, Instruction ins) throws Throwable {
		Signal[] s = c.visit(in[0]);
		if (s.length > 0 && s[0].type == INT)
			c.fmt("ret %s", c.typeVar(s[0]));
		else c.put("ret i32 0");
	}*/

	static Instruction pick(Node node, Instruction ins) throws Throwable {
		Signal[] in = node.in(0);
		out: for (Signal s : node.out) {
			if (s.type < POINTER || s.constant()) continue;
			for (Signal s1 : in)
				if (s1 == s) continue out;
			int[] par = type(s.type).par;
			Signal o = par.length <= 1 ? s : new Signal(par[0]);
			ins = ins.add(GETELEMENTPTR, o,
				in[(int)s.addr], new Signal(INT, 0),
				new Signal(INT, s.addr >> 32)
			);
			if (o != s) ins = ins.add(BITCAST, s, o);
		}
		return ins;
	}

	public static StringBuilder structVar(StringBuilder sb, Type p, Signal[] val) {
		int l0 = p.par.length, l1 = p.ret.length, l = l0 + min(l1, 1);
		if (l == 1 && l1 == 0) return sb.append("$v");
		if (l > 1) sb.append('{');
		if (l0 > 0) sb.append('$').append(l0).append("($t $<v$)");
		if (l1 > 0) {
			int n = (val.length - l0) / l1;
			sb.append(l0 > 0 ? ", [$" : "[$").append(n);
			if (l1 > 1) sb.append("({$").append(l1).append("[$t $<v$]}$)]");
			else sb.append("($t $<v$)]");
		}
		if (l > 1) sb.append('}');
		return sb;
	}

	private static final char[] hex = "0123456789ABCDEF".toCharArray();

	static Instruction ref(Node node, Instruction ins) throws Throwable {
		Signal ret = node.out[0];
		Signal[] arg = node.in(1);
		Type pt = type(ret.type);
		if (ret.constant()) {
			StringBuilder sb = new StringBuilder("$v = private unnamed_addr ");
			sb.append(pt.readOnly() ? "constant $<e " : "global $<e ");
			boolean zero = true, str = true;
			for (Signal s : arg){
				zero &= s.addr == 0L;
				str &= s.type == BYTE;
			}
			if (zero) sb.append("zeroinitializer\n");
			else if(str) {
				sb.append("c\"");
				for (Signal s : arg) {
					char c = (char)s.addr;
					if (c < 32 || c == '"' || c == '\\')
						sb.append('\\').append(hex[c >>> 4 & 15]).append(hex[c & 15]);
					else sb.append(c);
				}
				sb.append("\"\n");
			} else return ins.add(structVar(sb, pt, arg).append('\n').toString(), ret, arg);
			return ins.add(sb.toString(), ret);
		} else {
			ins = ins.add(ALLOCA, ret);
			//FIXME this will not work:
			StringBuilder sb = new StringBuilder("  store $e ");
			structVar(sb, pt, arg).append(", $0t $<v\n");
			return ins.add(sb.toString(), ret, arg);
		}
	}

	static Instruction idx(Node node, Instruction ins) {
		Signal[] ptr = node.in(0), idx = node.in(1), out = node.out;
		int lp = ptr.length - 1, li = idx.length - 1;
		for (int i = 0; i < out.length; i++) {
			Signal sp = ptr[min(i, lp)], si = idx[min(i, li)];
			int l = type(sp.type).par.length;
			ins = ins.add(GETELEMENTPTR, out[i],
				l == 0 ? new Signal[] {sp, si}
				: new Signal[] {sp, new Signal(INT, 0), new Signal(INT, l), si}
			);
		}
		return ins;
	}

	/**Unpack l elements from s into dst, starting at i.
	 * The struct s is represented with a pointer type. */
	private static Instruction unpackStruct(
		Instruction ins, Signal s, Signal[] dst, int i, int l
	) {
		for (int j = 0; j < l; j++)
			ins = ins.add(EXTRACTVALUE, dst[i++], s, new Signal(INT, j));
		return ins;
	}

	/**Pack l elements from src starting at i into s.
	 * The struct s is represented with a pointer type. */
	private static Instruction packStruct(
		Instruction ins, Signal s, Signal[] src, int i, int l
	) {
		Signal struct = new Signal(s.type, -1L);
		for (int j = 0; j < l; j++) {
			Signal str1 = j == l - 1 ? s : new Signal(s.type);
			ins = ins.add(INSERTVALUE, str1, struct, src[i++], new Signal(INT, j));
			struct = str1;
		}
		return ins;
	}

	static Instruction load(Node node, Instruction ins) {
		Signal[] out = node.out;
		int i = 0;
		for (Signal ptr : node.in(0)) {
			Type t = type(ptr.type);
			int l = t.par.length;
			if (l == 0) continue;
			boolean direct = l + t.ret.length == 1;
			Signal o = direct ? out[i++] : new Signal(ptr.type);
			ins = ins.add(LOAD, o, ptr);
			if (direct) continue;
			ins = unpackStruct(ins, o, out, i, l);
		}
		return ins;
	}

	static Instruction store(Node node, Instruction ins) {
		Signal[] val = node.in(1);
		int i = 0;
		for (Signal ptr : node.in(0)) {
			Type t = type(ptr.type);
			int l = t.par.length;
			if (l == 0) continue;
			Signal struct;
			if (l + t.ret.length > 1)
				ins = packStruct(ins, struct = new Signal(ptr.type), val, i, l);
			else struct = val[i++];
			ins = ins.add(STORE, null, struct, ptr);
		}
		return ins;
	}

	static Instruction call(Node node, Instruction ins) {
		Signal[] fun = node.in(0);
		int l = fun.length - 1;
		if (l < 0) return ins;
		Signal[] in = node.in(1);
		Signal[] arg = new Signal[in.length + 1];
		System.arraycopy(in, 0, arg, 1, in.length);
		for (int i = 0; i <= l; i++) {
			Type t = type((arg[0] = fun[i]).type);
			int rl = t.ret.length;
			Signal[] ret;
			if (i < l) {
				ret = new Signal[rl + 1];
				for (int j = 0; j < rl; j++)
					ret[j + 1] = new Signal(t.ret[j]);
			} else ret = node.out;
			Signal r = rl == 0 ? null : rl == 1 ? ret[ret.length - 1]
				: new Signal(new Type(HEAP, t.ret, Type.EMPTY).define(0));
			ins = ins.add(CALL, r, arg);
			if (rl > 1)
				ins = unpackStruct(ins, r, ret, i < l ? 1 : 0, rl);
			arg = ret;
		}
		return ins;
	}

	static Instruction swt(Node node, Instruction ins) throws Throwable {
		Signal bt = new Signal(LABEL), bf = new Signal(LABEL), end = new Signal(LABEL);
		//select branch
		ins = ins.add(BR, null, node.in(0)[0], bt, bf);
		//evaluate true branch
		ins = node.compIn(ins.add(bt), 1).add(BR, null, end);
		bt = ins.start.set;
		//evaluate false branch
		ins = node.compIn(ins.add(bf), 2).add(BR, null, end);
		bf = ins.start.set;
		//end switch
		ins = ins.add(end);
		Signal[] vt = node.in(1), vf = node.in(2), r = node.out;
		for (int i = 0; i < r.length; i++)
			ins = ins.add(PHI, r[i], vt[i], bt, vf[i], bf);
		return ins;
	}

	static Instruction loop(Node node, Instruction ins) throws Throwable {
		Signal loop = new Signal(LABEL), body = new Signal(LABEL), end = new Signal(LABEL);
		//enter the loop
		ins = ins.add(BR, null, loop);
		Signal start = ins.start.set;
		Instruction phi = ins = ins.add(loop); //remember insertion point for PHI instructions
		//evaluate while condition
		ins = node.compIn(ins, 1).add(BR, null, node.in(1)[0], body, end);
		//evaluate body
		ins = node.compIn(ins.add(body), 2).add(BR, null, loop);
		body = ins.start.set;
		//now since we know where body ends, insert the PHI instructions
		Signal[] out = node.out, init = node.in(0), state = node.in(2);
		Instruction next = phi.next;
		for (int i = 0; i < out.length; i++)
			phi = phi.add(PHI, out[i], init[i], start, state[i], body);
		phi.next = next;
		//exit loop
		return ins.add(end);
	}

	static Instruction def(Node node, Instruction ins) throws Throwable {
		Signal f = node.out[0];
		Signal[] par = new Signal[node.out.length - 1];
		System.arraycopy(node.out, 1, par, 0, par.length);
		ins = ins.add(DEFINE, f, par).add(new Signal(LABEL));
		ins = node.compIn(ins, 1);
		par = node.in(1);
		if (par.length == 0) return ins.add(RET_VOID, null);
		if (par.length > 1) {
			f = new Signal(new Type(HEAP, type(f.type).ret, Type.EMPTY).define(0));
			ins = packStruct(ins, f, par, 0, par.length);
		} else f = par[0];
		return ins.add(RET, null, f);
	}

	private static Instruction vec2(Node node, Instruction ins, String iop, String fop) {
		Signal[] a = node.in(0), b = node.in(1), out = node.out;
		int la = a.length - 1, lb = b.length - 1;
		for (int i = 0; i < out.length; i++) {
			Signal o = out[i];
			if (o.constant() && o.type < POINTER) continue;
			Signal sa = a[min(i, la)], sb = b[min(i, lb)];
			ins = ins.add(
				(sa.type == FLOAT || sa.type == DOUBLE ? fop : iop) + " $1t $<v, $v\n",
			o, sa, sb);
		}
		return ins;
	}

	static Instruction add(Node node, Instruction ins) {
		return vec2(node, ins, "  add" , "  fadd");
	}

	static Instruction sub(Node node, Instruction ins) {
		return vec2(node, ins, "  sub", "  fsub");
	}

	static Instruction mul(Node node, Instruction ins) {
		return vec2(node, ins, "  mul", "  fmul");
	}

	static Instruction div(Node node, Instruction ins) {
		return vec2(node, ins, "  sdiv", "  fdiv");
	}

	static Instruction mod(Node node, Instruction ins) {
		return vec2(node, ins, "  srem", "  frem");
	}

	static Instruction udiv(Node node, Instruction ins) {
		return vec2(node, ins, "  udiv", null);
	}

	static Instruction umod(Node node, Instruction ins) {
		return vec2(node, ins, "  urem", null);
	}

	static Instruction or(Node node, Instruction ins) {
		return vec2(node, ins, "  or", null);
	}

	static Instruction and(Node node, Instruction ins) {
		return vec2(node, ins, "  and", null);
	}

	static Instruction xor(Node node, Instruction ins) {
		return vec2(node, ins, "  xor", null);
	}

	static Instruction eq(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp eq", "  fcmp oeq");
	}

	static Instruction ne(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp ne", "  fcmp une");
	}

	static Instruction lt(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp slt", "  fcmp olt");
	}

	static Instruction gt(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp sgt", "  fcmp ogt");
	}

	static Instruction le(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp sle", "  fcmp ole");
	}

	static Instruction ge(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp sge", "  fcmp oge");
	}

	static Instruction ult(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp ult", null);
	}

	static Instruction ugt(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp ugt", null);
	}

	static Instruction ule(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp ule", null);
	}

	static Instruction uge(Node node, Instruction ins) {
		return vec2(node, ins, "  icmp uge", null);
	}

}
