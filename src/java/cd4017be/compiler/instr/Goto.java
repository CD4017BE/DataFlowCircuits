package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.*;


/**
 * 
 * @author CD4017BE */
public class Goto implements Instruction {

	public static final Goto REPEAT = new Goto(0), BREAK = new Goto(1), ELSE = REPEAT;


	private final int path;

	public Goto(int path) {
		this.path = path;
	}

	@Override
	public Value eval(Arguments args, ScopeData scope) {
		int n = args.ins();
		Value val;
		if (n == 0) val = Bundle.VOID;
		else if (n == 1) val = args.in(0);
		else val = new Bundle(args.inArr(0));
		return new SwitchSelector(path, val);
	}

	public Node node(int ins, int idx) {
		return new Node(this, Node.INSTR, ins, idx);
	}

}
