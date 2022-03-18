package cd4017be.dfc.compiler;

import static cd4017be.dfc.lang.GlobalVar.GLOBALS;
import static cd4017be.dfc.lang.Type.*;
import static java.lang.Math.min;

import java.io.*;

import cd4017be.dfc.lang.*;

/**Linked list of instructions that are represented by a formatting string and an array of arguments.
 * <p> Every occurrence of {@code $<index><mode>} is replaced depending on {@code <mode>}:
 * <li> {@code $} inserts a '$'. </li>
 * <li> {@code v} inserts the variable name or constant value of argument {@code <index>}. </li>
 * <li> {@code f} inserts the variable name of argument {@code <index>} as function parameter (with pointer access flags). </li>
 * <li> {@code t} inserts the type of argument {@code <index>}. </li>
 * <li> {@code e} inserts the pointer element type of argument {@code <index>}. </li>
 * <li> {@code r} inserts the function return type of argument {@code <index>}. </li>
 * <li> {@code p} inserts the function parameter types of argument {@code <index>}. </li>
 * <li> {@code .} sets {@code <index>} as next argument (inserts nothing). </li>
 * <li> {@code (} and {@code [} mark the start of a repeated sequence. </li>
 * <li> {@code )} and {@code ]} repeat everything from the corresponding start marker
 * {@code <index>} times with comma separation. </li>
 * </p><br>
 * <p> {@code <index>} can be either a decimal number or:
 * <li> {@code <} to reuse the last inserted parameter. </li>
 * <li> empty to to use the next parameter. </li>
 * </p><br>
 * @author CD4017BE */
public class Instruction {

	public Instruction next, start;
	public final String format;
	public final Signal set;
	public final Signal[] args;

	public Instruction(Instruction prev, String format, Signal set, Signal[] args) {
		this.format = format;
		this.set = set;
		this.args = args;
		if (prev == null) return;
		prev.next = this;
		if (set != null && set.constant()) return;
		start = prev.format == null ? prev : prev.start;
	}

	public Instruction add(Signal label) {
		return new Instruction(this, null, label, null);
	}

	public Instruction add(String format, Signal set, Signal... args) {
		return new Instruction(this, format, set, args);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Instruction i = this; i != null; i = i.next)
			sb.append(i.format);
		return sb.toString();
	}

	public static Instruction compile() {
		Instruction start = new Instruction(null, null, null, null);
		try {
			Instruction ins = start;
			for (GlobalVar var : GLOBALS)
				ins = var.node.compile(ins);
		} catch(Throwable e) {
			e.printStackTrace();
		}
		return start;
	}

	public static void initIds(Instruction ins) {
		for (int i = 0; ins != null; ins = ins.next) {
			Signal s = ins.set;
			if (s == null) continue;
			if (!s.constant()) {
				s.define(i++);
				continue;
			}
			i = 0;
			if (s.type >= 0) continue;
			for (Signal arg : ins.args)
				if (!arg.constant())
					arg.define(i++); 
		}
	}

	public static void print(Writer out, Instruction ins, boolean indices) throws IOException {
		for (; ins != null; ins = ins.next) {
			Signal s = ins.set;
			if (indices && s != null && !s.constant())
				if (s.type == LABEL) out.append(Long.toString(s.addr)).append(":\n");
				else out.append("  %").append(Long.toString(s.addr)).append(" =");
			String fmt = ins.format;
			if (fmt == null) continue;
			int p = 0, i = 0, na = 0, pa = 0, nb = 0, pb = 0, l = ins.args.length + 1;
			for(int q; (q = fmt.indexOf('$', p)) >= 0; p = q + 1) {
				out.append(fmt, p, q);
				char c = fmt.charAt(++q);
				boolean expl = false;
				int n = i;
				if (c == '<') {
					c = fmt.charAt(++q);
					n--;
				} else if (c >= '0' && c <= '9') {
					n = c - '0';
					while((c = fmt.charAt(++q)) >= '0' && c <= '9')
						n = n * 10 + c - '0';
					expl = true;
				}
				int et = 0;
				switch(c) {
				default: out.append(c); continue;
				case '(':
					if (i < l) {
						na = expl ? n : l - n;
						pa = q;
					} else q = fmt.indexOf("$)", q + 1) + 1;
					continue;
				case '[':
					if (i < l) {
						nb = expl ? n : l - n;
						pb = q;
					} else q = fmt.indexOf("$]", q + 1) + 1;
					continue;
				case ')':
					if (--na > 0 && i < l) {
						out.append(", ");
						q = pa;
					} continue;
				case ']':
					if (--nb > 0 && i < l) {
						out.append(", ");
						q = pb;
					} continue;
				case '.': i = n; continue;
				case 'v': out.append(ins.var(n)); break;
				case 'f': appendFlags(out.append(ins.var(n)), ins.arg(n).type); break;
				case 'r': out.append(putArrayType(new StringBuilder(), type(ins.arg(n).type).ret)); break;
				case 'p': parameters(out, ins.arg(n)); break;
				case 'e': et = 1;
				case 't':
					int t = (s = ins.arg(n)).type;
					String type = n != 0 || t < POINTER || !s.constant() || s.addr <= 0
						? typeName(t)
						: typeName(GLOBALS.get((int)s.addr - 1), t);
					out.append(type, 0, type.length() - et);
				}
				i = n + 1;
			}
			out.append(fmt, p, fmt.length());
		}
	}

	public Signal arg(int i) {
		return i == 0 ? set : args[i-1];
	}

	private String var(int i) {
		Signal s = arg(i);
		if (!s.constant()) return "%" + s.addr;
		return switch(s.type) {
		case UNKNOWN, LABEL -> throw new IllegalStateException();
		case TYPE -> typeName((int)s.addr);
		case BOOL -> s.addr != 0 ? "true" : "false";
		case BYTE, SHORT, INT, LONG -> Long.toString(s.addr);
		case FLOAT -> Float.toString(Float.intBitsToFloat((int)s.addr));
		case DOUBLE -> Double.toString(Double.longBitsToDouble(s.addr));
		default -> global(s, i == 0);
		};
	}

	private static String global(Signal s, boolean set) {
		if (s.addr < 0) return "undef";
		if (s.addr == 0) return "null";
		GlobalVar var = GLOBALS.get((int)s.addr - 1);
		if (set || var.len == 0) return var.name;
		return new StringBuilder("bitcast(")
		.append(typeName(var, s.type)).append(' ')
		.append(var.name).append(" to ")
		.append(typeName(s.type)).append(')')
		.toString();
	}

	private static String typeName(GlobalVar var, int t) {
		if (var.type != null) return var.type;
		Type type = type(t);
		StringBuilder sb = new StringBuilder();
		int l0 = type.par.length, l1 = type.ret.length;
		if (l0 + min(l1, 1) > 1) {
			putSeq(sb.append('{'), type.par);
			if (l1 != 0)
				putArrayType(sb.append(", [").append(var.len).append(" x "), type.ret).append(']');
			sb.append('}');
		} else if (l0 != 0) sb.append(typeName(type.par[0]));
		else putArrayType(sb.append("[").append(var.len).append(" x "), type.ret).append(']');
		return var.type = sb.append('*').toString();
	}

	private static void appendFlags(Writer out, int type) throws IOException {
		Type t = type(type);
		if (t == null) return;
		if (t.stackAlloc()) out.append(" nocapture");
		if (t.readOnly()) out.append(" readonly");
	}

	private static void parameters(Writer out, Signal f) throws IOException {
		int i = 0;
		for (int type : type(f.type).par) {
			if (i++ > 0) out.append(", ");
			out.append(typeName(type));
			appendFlags(out, type);
		}
	}

	private static final String[] TYPE_NAMES = {
		"i8", "type", "label", "i1", "i8", "i16", "i32", "i64", "float", "double"
	};

	public static String typeName(int t) {
		Type type = type(t);
		if (type == null) return TYPE_NAMES[t];
		if (type.name != null) return type.name;
		//type is not primitive or cached so create it:
		StringBuilder sb = new StringBuilder();
		int l0 = type.par.length, l1 = type.ret.length;
		if (type.function()) {
			putArrayType(sb, type.ret);
			putSeq(sb.append('('), type.par).append(')');
		} else {
			if (l0 + min(l1, 1) > 1) {
				putSeq(sb.append('{'), type.par);
				if (l1 != 0)
					putArrayType(sb.append(", [0 x "), type.ret).append(']');
				sb.append('}');
			} else if (l0 != 0) sb.append(typeName(type.par[0]));
			else if (l1 == 0) sb.append("i8");
			else putArrayType(sb, type.ret);
		}
		return type.name = sb.append('*').toString();
	}

	private static StringBuilder putArrayType(StringBuilder sb, int[] arr) {
		if (arr.length == 0) return sb.append("void");
		if (arr.length == 1) return sb.append(typeName(arr[0]));
		return putSeq(sb.append('{'), arr).append('}');
	}

	private static StringBuilder putSeq(StringBuilder sb, int[] arr) {
		if (arr.length == 0) return sb;
		for (int t : arr) sb.append(typeName(t)).append(", ");
		return sb.delete(sb.length() - 2, sb.length());
	}

}
