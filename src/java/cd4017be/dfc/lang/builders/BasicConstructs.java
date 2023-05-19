package cd4017be.dfc.lang.builders;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import cd4017be.dfc.lang.ArgumentParser;
import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.BlockDesc;
import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.NodeAssembler;
import cd4017be.dfc.lang.NodeContext;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.Value;
import cd4017be.dfc.lang.Module.SignalProvider;
import cd4017be.dfc.lang.instructions.ConstantIns;
import cd4017be.dfc.lang.instructions.PackIns;
import cd4017be.dfc.lang.instructions.VirtualCallIns;
import cd4017be.dfc.modules.core.Intrinsics;

/**
 * @author cd4017be */
public class BasicConstructs {

	public static final ArgumentParser IO_ARG = new ArgumentParser() {
		@Override
		public Node parse(String arg, BlockDesc block, int argidx, NodeContext context, int idx) throws SignalError {
			return context.getIO(arg);
		}
		@Override
		public void getAutoCompletions(BlockDesc block, int arg, ArrayList<String> list, NodeContext context) {
			list.addAll(context.links.keySet());
		}
	};
	public static final NodeAssembler OUTPUT = (block, context, idx) -> {
		String[] args = context.args(block);
		if (block.outs() != 0 || args.length != block.ins())
			throw new SignalError(idx, "wrong IO count");
		for (int i = 0; i < args.length; i++) {
			Node node = block.parser(i).parse(args[i], block, i, context, idx);
			if (node.in.length == 0)
				throw new SignalError(idx, "argument node has no inputs");
			block.ins[i] = node.in[0];
		}
	};
	public static final NodeAssembler INPUT = (block, context, idx) -> {
		Node[] args = block.getArgNodes(context, idx);
		if (block.ins() != 0 || args.length != block.outs())
			throw new SignalError(idx, "wrong IO count");
		for (int i = 0; i < args.length; i++)
			block.outs[i] = args[i];
	};
	public static final NodeAssembler PACK = (block, context, idx) -> {
		if (block.outs() != 1)
			throw new SignalError(idx, "wrong IO count");
		block.makeNode(new PackIns(), idx);
	};
	public static final NodeAssembler DEPEND = (block, context, idx) -> {
		if (block.ins() == 0) throw new SignalError(idx, "wrong IO count");
		Node node = new Node(null, Node.PASS, block.ins(), idx);
		block.setIns(node);
		block.makeOuts(node, idx);
	};
	public static final NodeAssembler LOOP = (block, context, idx) -> {
		if (block.def.ins.length != 2 && block.def.outs.length != 2)
			throw new SignalError(idx, "wrong IO count");
		Node state = new Node(null, Node.BEGIN, 0, idx);
		Node loop = new Node(null, Node.END, 3, idx);
		loop.in[0].connect(state);
		block.setIn(0, loop.in[2], idx);
		block.setIn(1, loop.in[1], idx);
		block.setOut(0, loop, idx);
		block.setOut(1, state, idx);
	};
	public static final NodeAssembler VIRTUAL = new NodeAssembler() {
		@Override
		public void assemble(BlockDesc block, NodeContext context, int idx)
		throws SignalError {
			Node node = new Node(makeVirtual(block.def), Node.INSTR, block.def.ins.length, idx);
			for (int i = 0; i < node.in.length; i++)
				block.setIn(i, node.in[i], idx);
			block.makeOuts(node, idx);
		}
		@Override
		public Instruction makeVirtual(BlockDef def) {
			String[] names = new String[def.ins.length];
			if (names.length == 1) names[0] = def.id;
			else for (int i = 0; i < names.length; i++)
				names[i] = def.id + "@" + i;
			return new VirtualCallIns(names);
		}
	};
	public static final ArgumentParser STRING_ARG = (arg, block, argidx, context, idx) -> {
		Value v = new Value(Intrinsics.STRING, Value.NO_ELEM, arg.isEmpty() ? Value.NO_DATA : arg.getBytes(UTF_8), 0);
		return new Node(new ConstantIns(v), Node.INSTR, 0, idx);
	};
	public static final ArgumentParser VALUE_ARG = new ArgumentParser() {
		@Override
		public Node parse(String arg, BlockDesc block, int argidx, NodeContext context, int idx)
		throws SignalError {
			return new Node(new ConstantIns(Intrinsics.parse(arg, context, idx, "value")), Node.INSTR, 0, idx);
		}
		@Override
		public void getAutoCompletions(BlockDesc block, int arg, ArrayList<String> list, NodeContext context) {
			for (SignalProvider sp : context.def.module.signals) {
				ConstList cl = sp.signals();
				if (cl != null)
					cl.getAutoCompletions(block, arg, list, context);
			}
		}
	};
	public static final NodeAssembler ERROR = (block, context, idx) -> {
		throw new SignalError(idx, "Invalid Block");
	};
	public static final ArgumentParser ERROR_ARG = (arg, block, argidx, context, idx) -> {
		throw new SignalError(idx, "Invalid Block");
	};

}
