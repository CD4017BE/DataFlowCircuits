package cd4017be.dfc.lang;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * 
 * @author CD4017BE */
public class PluginClassLoader extends ClassLoader {

	private final URL path;

	public PluginClassLoader(URL path) {
		this.path = path;
	}

	@Override
	protected URL findResource(String name) {
		try {
			return new URL(path, name);
		} catch(MalformedURLException e) {
			return null;
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		try {
			byte[] data = CircuitFile.loadResource(findResource(name + ".class"), 1 << 24);
			return defineClass(name, data, 0, data.length);
		} catch (IOException e) {
			throw new ClassNotFoundException(null, e);
		}
	}

}
