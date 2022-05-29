package cd4017be.dfc.compiler;

import static cd4017be.dfc.lang.type.Pointer.NO_CAPTURE;
import static cd4017be.dfc.lang.type.Pointer.NO_WRITE;
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

	public static final int NORMAL = -1, BRANCH = -2;

	public Instruction next;
	public final String format;
	public final Signal set;
	final Signal[] args;
	public final boolean branch;

	public Instruction(Instruction prev, String format, boolean branch, Signal set, Signal[] args) {
		this.format = format;
		this.set = set;
		this.args = args;
		this.branch = branch;
		if (prev == null) return;
		next = prev.next;
		prev.next = this;
	}

	public Instruction add(Signal label) {
		return new Instruction(this, null, false, label, null);
	}

	public Instruction add(String format, Signal set, Signal... args) {
		return new Instruction(this, format, false, set, args);
	}

	public Instruction addBr(String format, Signal set, Signal... args) {
		return new Instruction(this, format, true, set, args);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Instruction i = this; i != null; i = i.next)
			sb.append(i.format == null ? set == null ? "---\n" : "--:\n" : i.format);
		return sb.toString();
	}

	public void print(Writer out) throws IOException {
		String fmt = format;
		int p = 0, i = 0, na = 0, pa = 0, nb = 0, pb = 0, l = args.length + 1;
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
			case 'v': out.append(arg(n).toString()); break;
			case 'f': appendFlags(out.append(arg(n).toString()), arg(n).type); break;
			case 'r': out.append(((Function)arg(n).type).retType.toString()); break;
			case 'p': parameters(out, arg(n)); break;
			case 'e': et = 1;
			case 't':
				String type = arg(n).type.toString();
				out.append(type, 0, type.length() - et);
			}
			i = n + 1;
		}
		out.append(fmt, p, fmt.length());
	}

	public Signal arg(int i) {
		return i == 0 ? set : args[i-1];
	}

	private static void appendFlags(Writer out, Type type) throws IOException {
		if (!(type instanceof Pointer)) return;
		int flags = ((Pointer)type).flags;
		if ((flags & NO_CAPTURE) != 0) out.append(" nocapture");
		if ((flags & NO_WRITE) != 0) out.append(" readonly");
	}

	private static void parameters(Writer out, Signal f) throws IOException {
		Function fn = (Function)f.type;
		int i = 0;
		for (Type type : fn.parTypes) {
			if (i++ > 0) out.append(", ");
			out.append(type.toString());
			appendFlags(out, type);
		}
		if (fn.varArg)
			out.append(i == 0 ? "..." : ", ...");
	}

}
