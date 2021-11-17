package cd4017be.util;

/**Messages an OpenGL shader compilation error.
 * @author CD4017BE */
public class ShaderCompileException extends Exception {

	/** */
	private static final long serialVersionUID = 6956693284505290806L;

	public ShaderCompileException(String info) {
		super(info);
	}
}