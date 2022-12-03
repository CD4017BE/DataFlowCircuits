package cd4017be.dfc.editor;


/**
 * @author CD4017BE */
public interface CircuitObject {

	boolean inRange(int x0, int y0, int x1, int y1);
	short x();
	short y();
	CircuitObject pickup(CircuitEditor cc);
	CircuitObject place(CircuitEditor cc);
	CircuitObject pos(int x, int y, CircuitEditor cc);
	void add(CircuitEditor cc);
	void remove(CircuitEditor cc);
	void draw();

}
