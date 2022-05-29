package cd4017be.dfc.graph;

import cd4017be.dfc.editor.CircuitEditor;
import cd4017be.dfc.lang.*;
import cd4017be.dfc.lang.type.Types;

/**
 * 
 * @author CD4017BE */
public class Context {

	public final BlockRegistry reg;
	public SignalError errors = new SignalError(Node.NULL, 0, "");
	public Node firstUpdate, lastUpdate;
	public ExternalDefinitions extDef = new ExternalDefinitions();

	public Context(BlockRegistry reg) {
		this.reg = reg;
	}

	public void updateNode(Node node, int pin) {
		if (node.updating == 0)
			lastUpdate = lastUpdate == null
				? (firstUpdate = node)
				: (lastUpdate.nextUpdate = node);
		node.updating |= 1 << pin;
	}

	public boolean tick(int steps) {
		Node node = firstUpdate;
		for (; steps > 0 && node != null; steps--, node = node.clearUpdate()) {
			if (node.idx < 0) continue;
			try {
				node.clearError();
				Behavior b = node.def.behavior;
				if (b == null) b = reg.load(node.def);
				b.update(node, this);
			} catch (SignalError e) {
				node.updateOutput(null, this);
				errors.add(node.error = e);
			}
			if (node.macro instanceof CircuitEditor edit)
				edit.redrawSignal(node.idx);
		}
		if ((firstUpdate = node) != null) return true;
		lastUpdate = null;
		return false;
	}

	public void clear() {
		Types.clear();
		extDef.clear();
		firstUpdate = lastUpdate = null;
		while(errors.next != null) errors.next.remove();
	}

}
