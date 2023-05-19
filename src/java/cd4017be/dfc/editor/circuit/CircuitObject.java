package cd4017be.dfc.editor.circuit;

/**
 * @author CD4017BE */
public interface CircuitObject {

	boolean inRange(int x0, int y0, int x1, int y1);
	short x();
	short y();
	CircuitObject pickup();
	CircuitObject place();
	CircuitObject pos(int x, int y);
	void add(CircuitEditor cb);
	void remove();
	void draw();

}
