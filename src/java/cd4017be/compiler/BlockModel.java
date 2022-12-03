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
	public byte[] outs = new byte[0], ins = new byte[0];
	public byte tx = 4, ty = 2, tw = 2, th = 2;

	public BlockModel(Module module, String name) {
		this.module = module;
		this.name = name;
	}

	public void loadIcon() {
		if (icon != null) return;
		Path path = module.path.resolve("icons/" + name + ".tga");
		try (
			InputStream is = Files.newInputStream(path);
			MemoryStack ms = MemoryStack.stackPush();
		) {
			module.cache.icons.load(GLUtils.readTGA(is, ms), this);
			System.out.println("loaded icon " + path);
		} catch (IOException e) {
			icon = module.cache.defaultModel.icon;
			e.printStackTrace();
		}
	}

	public void setDynRegion(int x0, int y0, int x1, int y1) {
		tx = (byte)(x0 + x1 >> 2);
		ty = (byte)(y0 >> 1);
		tw = (byte)(x1 - x0 >> 2);
		th = (byte)(y1 - y0 >> 2);
	}

	@Override
	public AtlasSprite icon() {
		return icon;
	}

	@Override
	public float[] icon(AtlasSprite icon) {
		if (this.icon == null) {
			tx -= icon.w;
			tw = (byte)(icon.w - tw);
			th = (byte)(icon.h - th);
		}
		this.icon = icon;
		float w = 2 * icon.w;
		float h = 2 * icon.h;
		return new float[] {
			(float)(tx + tw) / w,
			(float)(ty     ) / h,
			(float)(tx - tw) / w + 1F,
			(float)(ty-2*th) / h + 1F,
		};
	}

	@Override
	public String toString() {
		return module + ":" + name;
	}

}
