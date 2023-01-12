package cd4017be.compiler.instr;

import cd4017be.compiler.*;
import cd4017be.compiler.builtin.*;


/**
 * 
 * @author CD4017BE */
public class Goto implements Instruction {

	public static final Goto BREAK = new Goto(0), CONTINUE = new Goto(1), ELSE = BREAK;


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
		else {
			Value[] arr = new Value[n];
			for (int i = 0; i < n; i++)
				arr[i] = args.in(i);
			val = new Bundle(arr);
		}
		return new SwitchSelector(path, val);
	}

	public Node node(int ins) {
		return new Node(this, Node.INSTR, ins);
	}

	public Node node(Node... in) {
		Node node = node(in.length);
		for (int i = 0; i < in.length; i++)
			node.in[i].connect(in[i]);
		return node;
	}

}
