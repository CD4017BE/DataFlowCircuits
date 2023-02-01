package cd4017be.compiler;

import java.util.ArrayList;
import cd4017be.compiler.Module.SignalProvider;
import cd4017be.compiler.builtin.*;
import cd4017be.compiler.instr.*;

/**
 * 
 * @author CD4017BE */
public interface Plugin {

	NodeAssembler assembler(String type, BlockDef def);

	Class<? extends Value> valueClass(String type);


	NodeAssembler TYPE = (block, context, idx) -> {
		if (block.ins.length != block.outs.length)
			throw new SignalError(idx, "wrong IO count");
		for (int i = 0; i < block.ins.length; i++) {
			Node node = new Node((args, scope) -> new Value(args.in(0).type), Node.INSTR, 1, idx);
			block.ins[i] = node.in[0];
			block.outs[i] = node;
		}
	};
	NodeAssembler ET = (block, context, idx) -> {
		String[] args = context.args(block);
		int l = args.length;
		if (block.ins.length != 1 || l != block.outs.length)
			throw new SignalError(idx, "wrong IO count");
		if (l != 1) {
			Node in = new Node(Instruction.PASS, Node.INSTR, 1, idx);
			for (int i = 0; i < l; i++) {
				Node node = new Node(new GetElementType(args[i]), Node.INSTR, 1, idx);
				block.outs[i] = node;
				node.in[0].connect(in);
			}
			block.setIns(in);
		} else block.makeNode(new GetElementType(args[0]), idx);
	};
	NodeAssembler NT = new NodeAssembler() {
		@Override
		public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
			String[] args = context.args(block);
			if (block.ins.length != args.length - 1)
				throw new SignalError(idx, "wrong IO count");
			block.makeNode(new NewType(args, context.def.module), idx);
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list, NodeContext context
		) {
			list.addAll(context.def.module.types.keySet());
		}
	};
	NodeAssembler OP = new NodeAssembler() {
		@Override
		public void assemble(BlockDesc block, NodeContext context, int idx)
		throws SignalError {
			String[] arg = context.args(block);
			if (arg.length != 1) throw new SignalError(idx, "wrong IO count");
			block.makeNode(
				arg[0].startsWith("g")
				? (args, scope) -> {
					DynOp op = new DynOp(args.in(0).type, args.inArr(1), true);
					Value old = scope.globals.putIfAbsent(op.values[0], op);
					if (old != null)
						return op.equals(old) ? old : args.error("global name conflict: " + op.values[0]);
					for (ScopeData parent; (parent = scope.parent) != null; scope = parent);
					scope.dynOps.add(op);
					return op;
				} : (args, scope) -> {
					DynOp op = new DynOp(args.in(0).type, args.inArr(1), false);
					scope.dynOps.add(op);
					return op;
				}, idx
			);
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list, NodeContext context
		) {
			list.add("g");
			list.add("l");
		}
	};
	NodeAssembler SET_EL = (block, context, idx) -> {
		String[] args = context.args(block);
		if (args.length != 1 || block.ins() != 2)
			throw new SignalError(idx, "wrong IO count");
		try {
			block.makeNode(new SetElement(Integer.parseUnsignedInt(args[0])), idx);
		} catch (NumberFormatException e) {
			throw new SignalError(idx, "can't parse index");
		}
	};
	NodeAssembler USE_COUNT = (block, context, idx) -> {
		if (block.ins() != 1) throw new SignalError(idx, "wrong IO count");
		block.makeNode((args, scope) ->
			args.in(0) instanceof DynOp op ? new CstInt(op.uses)
				: args.error("dynamic value expected")
		, idx);
	};
	NodeAssembler ERR = (block, context, idx) -> {
		String[] args = context.args(block);
		if (args.length != 1)
			throw new SignalError(idx, "wrong IO count");
		block.makeNode(new Abort(args[0]), idx);
	};
	NodeAssembler IO = new NodeAssembler() {
		@Override
		public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
			int l = block.outs.length + block.ins.length;
			if (block.args.length != l)
				throw new SignalError(idx, "wrong IO count");
			if (block.outs.length == 0)
				for (int i = 0; i < l; i++)
					block.ins[i] = context.getIO(block.args[i]).in[0];
			else if (block.ins.length == 0)
				for (int i = 0; i < l; i++)
					block.outs[i] = context.getIO(block.args[i]);
			else throw new SignalError(idx, "wrong IO count");
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list, NodeContext context
		) {
			list.addAll(context.links.keySet());
		}
	};
	NodeAssembler EXPR = new NodeAssembler() {
		@Override
		public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
			String[] args = context.args(block);
			if (block.ins.length != 0 || args.length != block.outs.length)
				throw new SignalError(idx, "wrong IO count");
			for (int i = 0; i < args.length; i++) {
				Value val = Value.parse(args[i], context, idx, "value");
				block.outs[i] = new Node(val != null ? val : (a, scope) -> null, Node.INSTR, 0, idx);
			}
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list,
			NodeContext context
		) {
			for (SignalProvider sp : context.def.module.signals) {
				ConstList cl = sp.signals();
				if (cl != null)
					cl.getAutoCompletions(block, arg, list, context);
			}
		}
	};
	NodeAssembler STRING = (block, context, idx) -> {
		String[] args = context.args(block);
		if (block.ins.length != 0 || args.length != block.outs.length)
			throw new SignalError(idx, "wrong IO count");
		for (int i = 0; i < args.length; i++)
			block.outs[i] = new Node(args[i].isEmpty() ? CstBytes.EMPTY : new CstBytes(args[i]), Node.INSTR, 0, idx);
	};
	NodeAssembler PACK = (block, context, idx) -> {
		if (block.ins() != 1 && block.outs() != 1)
			throw new SignalError(idx, "wrong IO count");
		block.makeNode(block.ins() == 1 ? Instruction.PASS : Instruction.PACK, idx);
	};
	SwitchAssembler CMP_TYPES = (block, context, idx) -> new Node(
		(args, scope) -> new SwitchSelector(args.in(0).type == args.in(1).type ? 1 : 0, null),
		Node.INSTR, 2, idx
	);
	SwitchAssembler CMP_VTABLE = (block, context, idx) -> new Node(
		(args, scope) -> new SwitchSelector(args.in(0).type.vtable == args.in(1).type.vtable ? 1 : 0, null),
		Node.INSTR, 2, idx
	);
	SwitchAssembler TYPE_SWITCH = new SwitchAssembler() {
		@Override
		public Node switchNode(BlockDesc block, NodeContext context, int idx) throws SignalError {
			String[] args = context.args(block);
			if (args.length + 2 != block.ins.length)
				throw new SignalError(idx, "wrong IO count");
			var mod = context.def.module;
			VTable[] cases = new VTable[args.length];
			for (int i = 0; i < cases.length; i++)
				if ((cases[i] = mod.findType(args[i])) == null)
					throw new SignalError(idx, "invalid type name: " + args[i]);
			return new TypeSwitch(cases).switchNode(block, context, idx);
		}
		@Override
		public void getAutoCompletions(
			BlockDesc block, int arg, ArrayList<String> list, NodeContext context
		) {
			var mod = context.def.module;
			list.addAll(mod.types.keySet());
			for (Module m : mod.imports.values())
				list.addAll(m.types.keySet());
		}
	};
	SwitchAssembler VALUE_SWITCH = (block, context, idx) -> {
		String[] args = context.args(block);
		if (args.length + 2 != block.ins.length)
			throw new SignalError(idx, "wrong IO count");
		Value[] cases = new Value[args.length];
		for (int i = 0; i < cases.length; i++)
			cases[i] = Value.parse(args[i], context, idx, "case" + i);
		return new CstSwitch(cases).switchNode(block, context, idx);
	};
	NodeAssembler LOOP = (block, context, idx) -> {
		if (block.ins() != 1)
			throw new SignalError(idx, "wrong IO count");
		Node pop = new Node((args, scope) -> {
			scope.parent.dynOps.addAll(scope.dynOps);
			scope.dynOps.clear();
			return args.in(0);
		}, Node.INSTR, 1, idx);
		block.setIns(pop);
		Node node = new Node((args, scope) -> ((SwitchSelector)args.in(0)).value, Node.END, 1, idx);
		node.in[0].connect(pop);
		block.makeOuts(node, idx);
	};
	NodeAssembler DO = (block, context, idx) -> {
		if (block.ins() != block.outs())
			throw new SignalError(idx, "wrong IO count");
		Node node0 = Goto.REPEAT.node(block.ins.length, idx);
		block.setIns(node0);
		Node node1 = new Node((args, scope) -> scope.value, Node.BEGIN, 1, idx);
		node1.in[0].connect(node0);
		block.makeOuts(node1, idx);
	};
	NodeAssembler BREAK = (block, context, idx) -> {
		if (block.outs() != 1)
			throw new SignalError(idx, "wrong IO count");
		block.makeNode(Goto.BREAK, idx);
	};
	NodeAssembler REPEAT = (block, context, idx) -> {
		if (block.outs() != 1)
			throw new SignalError(idx, "wrong IO count");
		block.makeNode(Goto.REPEAT, idx);
	};


	Plugin DEFAULT = new Plugin() {
		@Override
		public Class<? extends Value> valueClass(String type) {
			switch(type) {
			case "int": return CstInt.class;
			case "float": return CstFloat.class;
			case "bytes": return CstBytes.class;
			case "bundle": return Bundle.class;
			case "scope": return ScopeData.class;
			case "switch": return SwitchSelector.class;
			case "dyn": return DynOp.class;
			case "io": return IOStream.class;
			default: return Value.class;
			}
		}
		@Override
		public NodeAssembler assembler(String type, BlockDef def) {
			switch(type) {
			case "macro": return new Macro(def);
			case "block": return new Function(def);
			case "const": return new ConstList(def);
			case "to": return new VirtualCall(def.id);
			case "type": return TYPE;
			case "pack": return PACK;
			case "ce": return EXPR;
			case "str": return STRING;
			case "io": return IO;
			case "et": return ET;
			case "nt": return NT;
			case "op": return OP;
			case "se": return SET_EL;
			case "uc": return USE_COUNT;
			case "mv": return TYPE_SWITCH;
			case "cv": return CMP_VTABLE;
			case "ct": return CMP_TYPES;
			case "swt": return VALUE_SWITCH;
			case "do": return DO;
			case "br": return BREAK;
			case "rep": return REPEAT;
			case "loop": return LOOP;
			case "err": return ERR;
			default: return null;
			}
		}
	};

}
