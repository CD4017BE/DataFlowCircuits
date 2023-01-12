package cd4017be.compiler;

import cd4017be.compiler.builtin.*;
import cd4017be.compiler.instr.*;

/**
 * 
 * @author CD4017BE */
public class Interpreter {

	public static void main(String[] ca) {
		Node ni0 = new Node(0);
		Node nx0 = new Constant(new CstInt(1)).node();
		Node ndo1 = Goto.begin(ni0, nx0);
		Node ni = new GetElement(0).node(ndo1);
		Node nx = new GetElement(1).node(ndo1);
		Node nim1 = new Node(Interpreter::dec, Node.INSTR, ni);
		Node nxti = new Node(Interpreter::mul, Node.INSTR, nx, ni);
		Node nbr = new Goto(0).node(nxti);
		Node ncon = new Goto(1).node(nim1, nxti);
		Node nlp0 = new CstSwitch(new CstInt(0)).node(nim1, ncon, nbr);
		Node nlp1 = Goto.loop(nlp0);
		Node nret = new Node(nlp1);
		Function f = new Function(1);
		f.define(nret);
		System.out.println(f.eval(new Arguments(new CstInt(18)), new ScopeData(null, 0, null)));
	}

	private static Value dec(Arguments args, ScopeData scope) {
		return new CstInt(((CstInt)args.in(0)).value - 1);
	}

	private static Value mul(Arguments args, ScopeData scope) {
		return new CstInt(((CstInt)args.in(0)).value * ((CstInt)args.in(1)).value);
	}

}
