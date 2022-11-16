package cd4017be.compiler.ops;

import cd4017be.compiler.*;

/**
 * 
 * @author CD4017BE */
public enum StandardOps implements NodeOperator {
	CONST {
		@Override
		public void compValue(NodeState state) {
			state.outVal((Signal)state.data());
		}
	},
	INPUT {
		@Override
		public void compValue(NodeState state) {
			state.outVal(state.state.parent.inVal((int)state.data()));
		}
	},
	OUTPUT {
		@Override
		public void compValue(NodeState state) {
			int n = state.ins();
			Signal s;
			if (n == 0) s = Signal.DISCONNECTED;
			else if (n == 1) s = state.inVal(0);
			else {
				Signal[] ins = new Signal[n];
				for (int i = 0; i < n; i++)
					ins[i] = state.inVal(i);
				s = new Signal(ins);
			}
			state.state.pop(s);
		}
	},
	PASS {
		@Override
		public void compValue(NodeState state) {
			state.outVal(state.inVal(0));
		}
	},
	MACRO {
		@Override
		public void compValue(NodeState state) {
			new MacroState(state, (Macro)state.data());
		}
	},
	ELEMENT {
		@Override
		public void compValue(NodeState state) {
			state.outVal(state.inVal(0).src[(int)state.data()]);
		}
	},
	VIRTUAL {
		@Override
		public void compValue(NodeState state) {
			Signal a = state.inVal(0);
			a.type.vtable.get(state.data()).run(a, state);
		}
	};

}
