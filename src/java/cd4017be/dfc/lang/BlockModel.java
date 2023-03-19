package cd4017be.dfc.lang;

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
	public byte[] outs = new byte[2], ins = new byte[2];
	private float[] rep = new float[] { 4, 4, 12, 12 };
	public byte tx = 4, ty = 2, tw = 2, th = 2, rh = 2;

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
			LoadingCache.ATLAS.load(GLUtils.readTGA(is, ms), this);
			System.out.println("loaded icon " + path);
		} catch (IOException e) {
			icon = LoadingCache.MISSING_MODEL.icon;
			e.printStackTrace();
		}
	}

	public void setRepRegion(int x0, int y0, int x1, int y1) {
		rep[0] = x0;
		rep[1] = y0;
		rep[2] = x1;
		rep[3] = y1;
		rh = (byte)(y1 - y0 >> 2);
	}

	public void setTextRegion(int x0, int y0, int x1, int y1) {
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
			float w = 0.25F / (float)icon.w;
			float h = 0.25F / (float)icon.h;
			rep[0] *= w; rep[1] *= h;
			rep[2] *= w; rep[3] *= h;
		}
		this.icon = icon;
		return rep;
	}

	@Override
	public String toString() {
		return module + ":" + name;
	}

}
