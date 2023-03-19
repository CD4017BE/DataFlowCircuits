package cd4017be.dfc.lang;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 
 * @author CD4017BE */
public class PluginClassLoader extends ClassLoader {

	private final Path path;

	public PluginClassLoader(Path path) {
		this.path = path;
	}

	@Override
	protected URL findResource(String name) {
		try {
			return path.resolve(name).toUri().toURL();
		} catch(MalformedURLException e) {
			return null;
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		try {
			byte[] data = Files.readAllBytes(path.resolve(name + ".class"));
			return defineClass(name, data, 0, data.length);
		} catch (IOException e) {
			throw new ClassNotFoundException(null, e);
		}
	}

}
