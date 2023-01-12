package cd4017be.compiler;

import java.util.ArrayList;
import cd4017be.compiler.builtin.*;
import cd4017be.compiler.instr.*;

/**
 * 
 * @author CD4017BE */
public interface Plugin {

	NodeAssembler assembler(String type, BlockDef def);

	Class<? extends Value> valueClass(String type);


	NodeAssembler ET = (block, context) -> {
		int l = block.args.length;
		if (block.ins.length != 1 || l != block.outs.length)
			throw new IllegalArgumentException("wrong IO count");
		if (l != 1) {
			Node in = new Node(Instruction.PASS, Node.INSTR, 1);
			for (int i = 0; i < l; i++) {
				Node node = new Node(new GetElementType(block.args[i]), Node.INSTR, 1);
				block.outs[i] = node;
				node.in[0].connect(in);
			}
			block.setIns(in);
		} else block.makeNode(new GetElementType(block.args[0]));
	};
	NodeAssembler NT = new NodeAssembler() {
		@Override
		public void assemble(BlockDesc block, NodeContext context) {
			if (block.ins.length != block.args.length - 1)
				throw new IllegalArgumentException("wrong IO count");
			block.makeNode(new NewType(block.args, context.def.module));
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list, NodeContext context
		) {
			list.addAll(context.def.module.types.keySet());
		}
	};
	NodeAssembler OP = (block, context) -> {
		if (block.args.length != 1) throw new IllegalArgumentException("wrong IO count");
		block.makeNode(new DynInstruction(block.args[0]));
	};
	NodeAssembler ERR = (block, context) -> {
		if (block.ins.length != 0 || block.args.length != 1)
			throw new IllegalArgumentException("wrong IO count");
		block.makeNode(new SignalError(block.args[0]));
	};
	NodeAssembler IO = new NodeAssembler() {
		@Override
		public void assemble(BlockDesc block, NodeContext context) {
			int l = block.outs.length + block.ins.length;
			if (block.args.length != l)
				throw new IllegalArgumentException("wrong IO count");
			if (block.outs.length == 0)
				for (int i = 0; i < l; i++)
					block.ins[i] = context.getIO(block.args[i]).in[0];
			else if (block.ins.length == 0)
				for (int i = 0; i < l; i++)
					block.outs[i] = context.getIO(block.args[i]);
			else throw new IllegalArgumentException("wrong IO count");
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list, NodeContext context
		) {
			list.addAll(context.links.keySet());
		}
	};
	NodeAssembler EXPR = (block, context) -> {
		if (block.ins.length != 0 || block.args.length != block.outs.length)
			throw new IllegalArgumentException("wrong IO count");
		for (int i = 0; i < block.args.length; i++)
			block.outs[i] = new Node(Value.parse(block.args[i], context), Node.INSTR, 0);
	};
	SwitchAssembler CMP_TYPES = (block, context) -> new Node(
		(args, scope) -> new SwitchSelector(args.in(0).type == args.in(1).type ? 1 : 0, null),
		Node.INSTR, 2
	);
	SwitchAssembler CMP_VTABLE = (block, context) -> new Node(
		(args, scope) -> new SwitchSelector(args.in(0).type.vtable == args.in(1).type.vtable ? 1 : 0, null),
		Node.INSTR, 2
	);
	SwitchAssembler TYPE_SWITCH = new SwitchAssembler() {
		@Override
		public Node switchNode(BlockDesc block, NodeContext context) {
			if (block.args.length != block.ins.length + 2)
				throw new IllegalArgumentException("wrong IO count");
			VTable[] cases = new VTable[block.args.length];
			for (int i = 0; i < cases.length; i++)
				cases[i] = context.def.module.types.get(block.args[i]);
			return new TypeSwitch(cases).switchNode(block, context);
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list, NodeContext context
		) {
			list.addAll(context.def.module.types.keySet());
		}
	};
	SwitchAssembler VALUE_SWITCH = (block, context) -> {
		if (block.args.length != block.ins.length + 2)
			throw new IllegalArgumentException("wrong IO count");
		Value[] cases = new Value[block.args.length];
		for (int i = 0; i < cases.length; i++)
			cases[i] = Value.parse(block.args[i], context);
		return new CstSwitch(cases).switchNode(block, context);
	};
	NodeAssembler LOOP = (block, context) -> {
		Node node = new Node((args, scope) -> ((SwitchSelector)args.in(0)).value, Node.END, 1);
		block.setIns(node);
		block.makeOuts(node);
	};
	NodeAssembler DO = (block, context) -> {
		Node node0 = Goto.CONTINUE.node(block.ins.length);
		block.setIns(node0);
		Node node1 = new Node((args, scope) -> scope.value, Node.BEGIN, 1);
		node1.in[0].connect(node0);
		block.makeOuts(node1);
	};


	Plugin DEFAULT = new Plugin() {
		@Override
		public Class<? extends Value> valueClass(String type) {
			switch(type) {
			case "int": return CstInt.class;
			case "float": return CstFloat.class;
			case "bytes": return CstBytes.class;
			case "bundle": return Bundle.class;
			case "dyn": return DynOp.class;
			default: return Value.class;
			}
		}
		@Override
		public NodeAssembler assembler(String type, BlockDef def) {
			switch(type) {
			case "block": return new Function(def);
			case "const": return new ConstList(def);
			case "to": return new VirtualCall(def.id);
			case "ce": return EXPR;
			case "io": return IO;
			case "et": return ET;
			case "nt": return NT;
			case "op": return OP;
			case "mv": return TYPE_SWITCH;
			case "cv": return CMP_VTABLE;
			case "ct": return CMP_TYPES;
			case "swt": return VALUE_SWITCH;
			case "do": return DO;
			case "loop": return LOOP;
			case "err": return ERR;
			default: return null;
			}
		}
	};

}
