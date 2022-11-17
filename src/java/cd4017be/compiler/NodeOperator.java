package cd4017be.compiler;


/**
 * 
 * @author CD4017BE */
@FunctionalInterface
public interface NodeOperator {

	SignalError compValue(NodeState ns);

	interface Scoped extends NodeOperator {
		void compScope(NodeState state, Scope scope);
	}

	NodeOperator
	CONST = ns -> ns.outVal((Signal)ns.data()),
	INPUT = ns -> ns.outVal(ns.state.inVal((int)ns.data())),
	PASS = ns -> ns.outVal(ns.inVal(0)),
	MACRO = ns -> new MacroState(ns, (Macro)ns.data()).errors,
	ELEMENT = ns -> ns.outVal(ns.inVal(0).src[(int)ns.data()]),
	VIRTUAL = ns -> {
		Signal a = ns.inVal(0);
		String name = (String)ns.data();
		VirtualMethod vm = a.type.vtable.get(name);
		return vm != null ? vm.run(a, ns)
			: new SignalError("unsupported operation: " + name);
	};

	Scoped OUTPUT = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			int n = ns.ins();
			Signal s;
			if (n == 0) s = Signal.DISCONNECTED;
			else if (n == 1) s = ns.inVal(0);
			else {
				Signal[] ins = new Signal[n];
				for (int i = 0; i < n; i++)
					ins[i] = ns.inVal(i);
				s = new Signal(ins);
			}
			return ns.state.pop(s);
		}
		@Override
		public void compScope(NodeState ns, Scope scope) {
			ns.scope(ns.state.scope(), -1L);
		}
	};

}
