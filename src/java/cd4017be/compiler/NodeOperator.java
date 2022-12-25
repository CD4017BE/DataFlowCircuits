package cd4017be.compiler;

import java.util.Arrays;

import cd4017be.compiler.builtin.Bundle;
import cd4017be.compiler.builtin.CstFloat;
import cd4017be.compiler.builtin.CstInt;
import cd4017be.compiler.builtin.CstLoop;
import cd4017be.compiler.builtin.DynOp;

/**
 * 
 * @author CD4017BE */
@FunctionalInterface
public interface NodeOperator {

	SignalError compValue(NodeState ns);

	interface Scoped extends NodeOperator {
		void compScope(NodeState ns, Scope scope);
	}

	NodeOperator CONST = ns -> ns.out((Value)ns.data(), null);
	NodeOperator INPUT = ns -> ns.out(ns.state.inVal((int)ns.data()));
	NodeOperator PASS = ns -> ns.out(ns.in(0));
	NodeOperator MACRO = ns -> new MacroState(ns, (Macro)ns.data()).errors;
	NodeOperator ELEMENT = ns -> {
		NodeState a = ns.in(0);
		return ns.out(((Bundle)a.value).values[(int)ns.data()], a.se);
	};
	NodeOperator VIRTUAL = ns -> {
		String name = (String)ns.data();
		NodeState a = ns.in(0);
		VirtualMethod vm = a.value.type.vtable.get(name);
		if (vm != null) return vm.run(a, ns);
		if (ns.ins() == 2) vm = ns.in(1).value.type.vtable.get('r' + name);
		return vm != null ? vm.run(a, ns)
			: new SignalError("inputs don't support " + name);
	};
	NodeOperator EL_TYPE = ns -> {
		NodeState a = ns.in(0);
		Type t = a.value.type;
		Object o = ns.data();
		int i = o instanceof String s ? t.index(s) : (int)o;
		return ns.out(new Value(t.elem(i)), null);
	};
	NodeOperator NEW_TYPE = ns -> {
		String[] args = (String[])ns.data();
		String arg = args[0];
		int n = 0;
		int i = arg.lastIndexOf('#');
		if (i >= 0) {
			n = Integer.parseInt(arg.substring(i + 1));
			arg = arg.substring(0, i);
		}
		String[] names = Arrays.copyOfRange(args, 1, args.length);
		Type[] elem = new Type[names.length];
		for (int j = 0; j < elem.length; j++)
			elem[j] = ns.in(j).value.type;
		VTable vt = ns.state.macro.def.module.types.get(arg);
		if (vt == null) vt = Bundle.BUNDLE.vtable;
		Type t = Type.of(vt, names, elem, n);
		return ns.out(new Value(t), null);
	};
	NodeOperator OPERATION = ns -> {
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
		return ns.out(new DynOp(t, (String)ns.data(), ins), e);
	};
	NodeOperator ERROR = ns -> new SignalError((String)ns.data());

	Scoped OUTPUT = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			int n = ns.ins();
			Value v;
			SideEffects e = null;
			if (n == 0) v = Bundle.VOID;
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
				v = new Bundle(ins);
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
	Scoped CMP_SWT = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			Value val = ns.in(0).value;
			int cmp;
			if (val instanceof CstInt cv) {
				long v = cv.value;
				cmp = v > 0 ? 1 : v < 0 ? 3 : 2;
			} else if (val instanceof CstFloat cv) {
				double v = cv.value;
				cmp = v > 0 ? 1 : v < 0 ? 3 : 2;
			} else return new SignalError("expected constant number");
			return ns.out(ns.inScopeUpdate(cmp));
		}
		@Override
		public void compScope(NodeState ns, Scope scope) {
			ns.scope(scope, 0b0001L);
		}
	};
	Scoped DO = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			return ns.out(ns.in(0));
		}
		@Override
		public void compScope(NodeState ns, Scope scope) {
			ns.scope(scope == null ? null : scope.parent, -1L);
			if (scope instanceof CstLoop cl)
				ns.value = cl.value;
		}
	};
	Scoped LOOP = new Scoped() {
		@Override
		public SignalError compValue(NodeState ns) {
			if (!(ns.in(0).value instanceof CstInt cv))
				return new SignalError("expected const int for loop condition");
			if (cv.value == 0)
				return ns.out(ns.inScopeUpdate(2));
			NodeState next = ns.inScopeUpdate(1);
			CstLoop scope = (CstLoop)ns.scope;
			if (next != null && next.value != scope.value)
				ns.scope(new CstLoop(scope.parent, next.value), 0b001L);
			return null;
		}
		@Override
		public void compScope(NodeState ns, Scope scope) {
			if (scope == null) {
				ns.scope(scope, -1L);
				return;
			}
			Scope old = ns.scope;
			if (old == null || old.parent != scope)
				ns.scope(new CstLoop(scope, null), 0b001L);
		}
	};

}
