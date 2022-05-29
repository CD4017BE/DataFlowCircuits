package cd4017be.dfc.compiler;

import static cd4017be.dfc.lang.type.Primitive.LABEL;

import java.io.IOException;
import java.io.Writer;

import cd4017be.dfc.graph.Node;
import cd4017be.dfc.lang.Signal;
import cd4017be.dfc.lang.type.Function;
import cd4017be.dfc.lang.type.Types;

/**
 * 
 * @author CD4017BE */
public class Compiler {

	private static int LAST_PASS;
	private final Instruction first;
	private int lastGlobalIdx = -1;

	public Compiler(Node end) throws CompileError {
		this.first = new Instruction(null, null, false, null, null);
		int pass = ++LAST_PASS;
		NodeInstruction root = new NodeInstruction(null, end, first);
		for (NodeInstruction ni = root; ni != null; ni = ni.next)
			if ((end = ni.node).needsCompile(pass))
				end.def.compiler.compile(ni, this);
	}

	public Instruction addGlobal(String format, boolean branch, Signal set, Signal... args) {
		if (set.value instanceof Node n && !(n.data instanceof String))
			n.data = ++lastGlobalIdx;
		return new Instruction(first, format, branch, set, args);
	}

	public void resolveIds() {
		int id = 0, label = 0;
		for (Instruction ins0 = first, ins; (ins = ins0.next) != null; ins0 = ins) {
			Signal s = ins.set;
			if (ins.format == null) {
				//remove empty instructions
				ins0.next = ins.next;
				ins = ins0;
				if (s != null && s.type == LABEL)
					s.value = label;
				continue;
			}
			if (s != null)
				//assign instruction ID
				if (s.isVar()) s.value = id++;
				else {
					if (s.value instanceof Node n && n.data instanceof Integer idx)
						n.data = lastGlobalIdx - idx;
					id = 0;
					for (Signal arg : ins.args)
						if (arg.isVar())
							arg.value = id++;
				}
			//labels
			if (ins.branch) label = id++;
		}
	}

	public void assemble(Writer out, boolean indices) throws IOException {
		Types.writeTypeDefs(out);
		Instruction ins = first;
		if (!indices)
			while((ins = ins.next) != null)
				ins.print(out);
		else for (int id = 0; (ins = ins.next) != null;) {
			Signal s = ins.set;
			if (s != null)
				if (s.isVar())
					out.append(" %").append(Integer.toString(id = (int)s.value)).append(" =");
				else if (s.type instanceof Function)
					id = ins.args.length - 1;
			ins.print(out);
			if (ins.branch)
				out.append(Long.toString(++id)).append(":\n");
		}
	}

}
