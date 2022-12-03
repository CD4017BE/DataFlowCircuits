package cd4017be.compiler;


/**
 * 
 * @author CD4017BE */
@FunctionalInterface
public interface NodeOperator {

	SignalError compValue(NodeState ns);

	interface Scoped extends NodeOperator {
		void compScope(NodeState ns, Scope scope);
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
	},
	EL_TYPE = ns -> {
		NodeState a = ns.in(0);
		Type t = a.value.type;
		Object o = ns.data();
		int i = o instanceof String s ? t.index(s) : (int)o;
		return ns.out(new Value(t.elem(i), null), null);
	},
	NEW_TYPE = ns -> {
		Type t = (Type)ns.data();
		t = new Type(t.vtable, ns.in(0).value.type, t.n);
		return ns.out(new Value(t, null), null);
	},
	OPERATION = ns -> {
		Type t = ns.in(0).value.type;
		Value[] ins = new Value[ns.ins() - 1];
		SideEffects e = null;
		for (int i = 0; i < ins.length; i++) {
			NodeState s = ns.in(i + 1);
			ins[i] = s.value;
			SideEffects se = s.se;
			if (e == null) e = se;
			else if (se != null && se != e)
				e = new SideEffects(e, s.se, null);
		}
		return ns.out(new Value(t, ns.data(), ins), e);
	},
	ERROR = ns -> new SignalError((String)ns.data());

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
	Scoped MATCH_VT = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			boolean cond = ns.in(0).value.type.vtable == ns.data();
			return ns.out(ns.inScopeUpdate(cond ? 1 : 2));
		}
		@Override
		public void compScope(NodeState ns, Scope scope) {
			ns.scope(scope, 0b001L);
		}
	};
	Scoped COMP_VT = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			boolean cond = ns.in(0).value.type.vtable == ns.in(1).value.type.vtable;
			return ns.out(ns.inScopeUpdate(cond ? 2 : 3));
		}
		@Override
		public void compScope(NodeState ns, Scope scope) {
			ns.scope(scope, 0b0011L);
		}
	};
	Scoped COMP_TYPE = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			boolean cond = ns.in(0).value.type == ns.in(1).value.type;
			return ns.out(ns.inScopeUpdate(cond ? 2 : 3));
		}
		@Override
		public void compScope(NodeState ns, Scope scope) {
			ns.scope(scope, 0b0011L);
		}
	};

}
