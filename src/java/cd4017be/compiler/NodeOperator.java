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
	CONST = ns -> ns.out((Value)ns.data(), null),
	INPUT = ns -> ns.out(ns.state.inVal((int)ns.data())),
	PASS = ns -> ns.out(ns.in(0)),
	MACRO = ns -> new MacroState(ns, (Macro)ns.data()).errors,
	ELEMENT = ns -> {
		NodeState a = ns.in(0);
		return ns.out(a.value.args[(int)ns.data()], a.se);
	},
	VIRTUAL = ns -> {
		String name = (String)ns.data();
		NodeState a = ns.in(0);
		VirtualMethod vm = a.value.type.vtable.get(name);
		return vm != null ? vm.run(a, ns)
			: new SignalError("unsupported operation: " + name);
	};

	Scoped OUTPUT = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			int n = ns.ins();
			Value v;
			SideEffects e = null;
			if (n == 0) v = Value.VOID;
			else if (n == 1) {
				NodeState s = ns.in(0);
				v = s.value;
				e = s.se;
			} else {
				Value[] ins = new Value[n];
				for (int i = 0; i < n; i++) {
					NodeState s = ns.in(i);
					ins[i] = s.value;
					SideEffects se = s.se;
					if (e == null) e = se;
					else if (se != null && se != e)
						e = new SideEffects(e, s.se, null);
				}
				v = new Value(Type.VOID, null, ins);
			}
			return ns.state.pop(v, e);
		}
		@Override
		public void compScope(NodeState ns, Scope scope) {
			ns.scope(ns.state.scope(), -1L);
		}
	};

}
