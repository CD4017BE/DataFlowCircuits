package cd4017be.dfc.lang;

import java.io.IOException;
import java.io.InputStream;
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
		if (name.startsWith("/modules/")) name = name.substring(9);
		else if (name.startsWith("modules/")) name = name.substring(8);
		else return null;
		try {
			return new URL(path, name);
		} catch(MalformedURLException e) {
			return null;
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		URL url = findResource(name + ".class");
		if (url == null) throw new ClassNotFoundException("resource not found");
		try (InputStream is = url.openStream()) {
			byte[] data = is.readAllBytes();
			return defineClass(name, data, 0, data.length);
		} catch (IOException e) {
			throw new ClassNotFoundException(null, e);
		}
	}

}
