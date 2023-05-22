package cd4017be.dfc.lang.builders;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import cd4017be.dfc.lang.BlockDef;
import cd4017be.dfc.lang.BlockDesc;
import cd4017be.dfc.lang.CircuitFile;
import cd4017be.dfc.lang.Instruction;
import cd4017be.dfc.lang.Node;
import cd4017be.dfc.lang.NodeAssembler;
import cd4017be.dfc.lang.NodeContext;
import cd4017be.dfc.lang.ScopeBranch;
import cd4017be.dfc.lang.SignalError;
import cd4017be.dfc.lang.instructions.FunctionIns;
import cd4017be.util.ExtInputStream;

/**
 * @author cd4017be */
public class Function implements NodeAssembler {
		public final BlockDef def;
		public final int par;
		private MethodHandle intrinsic;
		public Instruction[] code;
		public int[] vars;
		public int ret;

		public Function(BlockDef def) {
			this.def = def;
			this.par = def.ins.length + def.args.length - (def.assembler instanceof SwitchBuilder ? 1 : 0);
		}

		public void reset() {
			this.code = null;
			this.vars = null;
		}

		public void define(Node out) throws SignalError {
			this.vars = new int[Node.evalScopes(out, par + 1)];
			ScopeBranch sb = (ScopeBranch)out.in[0].scope();
			this.ret = out.in[0].addr(sb.addr);
			this.code = sb.compile(vars);
		}

		public String[] compile() throws SignalError {
			NodeContext cont = new NodeContext(def, false);
			try (ExtInputStream is = CircuitFile.readBlock(def)) {
				cont.build(CircuitFile.readCircuit(is, def.module), true);
			} catch(IOException e) {
				throw new SignalError(-1, "can't load circuit", e);
			}
			Node out = new Node();
			String[] outs = cont.collectOutputs(out);
			define(out);
			return outs;
		}

		public void load() throws SignalError {
			synchronized(def) {
				if (vars == null) compile();
			}
		}

		@Override
		public void assemble(BlockDesc block, NodeContext context, int idx) throws SignalError {
			Node node = new Node(makeVirtual(block.def), Node.INSTR, par, idx);
			Node[] args = block.getArgNodes(context, idx);
			for (int i = 0; i < args.length; i++)
				node.in[i].connect(args[i]);
			int m = def.ins.length;
			for (int i = 0, j = args.length; i < m; i++, j++)
				block.setIn(i, node.in[j], idx);
			if (def.outs.length == 0 && block.args.length != 0)
				context.getIO(block.args[0]).in[0].connect(node);
			else block.makeOuts(node, idx);
		}

		@Override
		public Instruction makeVirtual(BlockDef def) {
			if (intrinsic != null) try {
				return (Instruction)intrinsic.invoke();
			} catch(Throwable e) {
				e.printStackTrace();
			}
			return new FunctionIns(this);
		}

		@Override
		public void setIntrinsic(MethodHandle impl) {
			intrinsic = impl;
		}

		@Override
		public boolean hasCircuit() {
			return intrinsic == null;
		}

	}