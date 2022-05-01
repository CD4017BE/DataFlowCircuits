package cd4017be.dfc.editor;


/**
 * @author CD4017BE */
public interface IMovable {

	boolean inRange(int x0, int y0, int x1, int y1);
	short x();
	short y();
	IMovable pickup();
	IMovable place();
	IMovable pos(int x, int y);
	void remove();

}
