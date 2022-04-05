package cd4017be.dfc.compiler;

import static cd4017be.dfc.lang.GlobalVar.GLOBALS;
import static cd4017be.dfc.lang.type.Pointer.NO_CAPTURE;
import static cd4017be.dfc.lang.type.Pointer.READ_ONLY;
import static cd4017be.dfc.lang.type.Primitive.LABEL;
import java.io.*;

import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.type.*;

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
		if (set != null && set.isConst()) return;
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
			if (!s.isConst()) {
				s.value = i++;
				continue;
			}
			i = 0;
			if (s.type instanceof Function)
				for (Signal arg : ins.args)
					arg.value = i++; 
		}
	}

	public static void print(Writer out, Instruction ins, boolean indices) throws IOException {
		Types.writeTypeDefs(out);
		for (; ins != null; ins = ins.next) {
			Signal s = ins.set;
			if (indices && s != null && !s.isConst())
				if (s.type == LABEL) out.append(Long.toString(s.value)).append(":\n");
				else out.append("  %").append(Long.toString(s.value)).append(" =");
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
				case 'v': out.append(ins.arg(n).toString()); break;
				case 'f': appendFlags(out.append(ins.arg(n).toString()), ins.arg(n).type); break;
				case 'r': out.append(((Function)ins.arg(n).type).retType.toString()); break;
				case 'p': parameters(out, ins.arg(n)); break;
				case 'e': et = 1;
				case 't':
					String type = ins.arg(n).type.toString();
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

	private static void appendFlags(Writer out, Type type) throws IOException {
		if (!(type instanceof Pointer)) return;
		int flags = ((Pointer)type).flags;
		if ((flags & NO_CAPTURE) != 0) out.append(" nocapture");
		if ((flags & READ_ONLY) != 0) out.append(" readonly");
	}

	private static void parameters(Writer out, Signal f) throws IOException {
		int i = 0;
		for (Type type : ((Function)f.type).parTypes) {
			if (i++ > 0) out.append(", ");
			out.append(type.toString());
			appendFlags(out, type);
		}
	}

}
