package cd4017be.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lwjgl.system.MemoryStack;

import cd4017be.util.*;
import cd4017be.util.IconAtlas.IconHolder;

/**
 * 
 * @author CD4017BE */
public class BlockModel implements IconHolder {

	public final Module module;
	public final String name;
	public AtlasSprite icon;
	public byte[] outs, ins;
	public byte tx, ty, tw, th;

	public BlockModel(Module module, String name) {
		this.module = module;
		this.name = name;
	}

	public void loadIcon() {
		if (icon != null) return;
		Path path = module.path.resolve("icons/" + name + ".tga");
		System.out.println("loading icon: " + path);
		try (
			InputStream is = Files.newInputStream(path);
			MemoryStack ms = MemoryStack.stackPush();
		) {
			module.cache.icons.load(GLUtils.readTGA(is, ms), this);
		} catch (IOException e) {
			icon = module.cache.defaultModel.icon;
			e.printStackTrace();
		}
	}

	@Override
	public AtlasSprite icon() {
		return icon;
	}

	@Override
	public void icon(AtlasSprite icon) {
		this.icon = icon;
	}

}
