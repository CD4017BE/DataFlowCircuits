package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.HEIGHT;
import static cd4017be.dfc.editor.Main.WIDTH;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.min;
import static org.lwjgl.glfw.GLFW.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author CD4017BE */
public class FileBrowser implements IGuiSection {

	final Consumer<File> result;
	final String title;
	final ArrayList<String> names = new ArrayList<>(), filtered = new ArrayList<>();
	final TextField text = new TextField(this::filter);
	final Predicate<String> filter;
	File dir;
	int sel;

	public FileBrowser(File dir, String title, Predicate<String> filter, Consumer<File> result) {
		this.result = result;
		this.title = title;
		this.filter = filter;
		boolean isdir = dir.isDirectory();
		this.dir = isdir ? dir : dir.getParentFile();
		enter("./");
		if (!isdir) text.set(dir.getName(), -1);
		Main.GUI.add(this);
		Main.lockFocus(this);
		Main.refresh(0);
	}

	private void enter(String name) {
		if (!isDir(name)) {
			if (filter.test(name))
				exit(new File(dir, name));
			return;
		}
		try {
			dir = new File(dir, name.substring(0, name.length() - 1)).getCanonicalFile();
		} catch(IOException e) {
			e.printStackTrace();
		}
		dir.mkdirs();
		names.clear();
		names.add("./");
		names.add("../");
		for (File f : dir.listFiles()) {
			String n = f.getName();
			if (f.isDirectory())
				names.add(n.concat("/"));
			else if (filter.test(n))
				names.add(n);
		}
		Collections.sort(names, FileBrowser::order);
		text.set("", -1);
		filtered.clear();
		filtered.addAll(names);
		sel = -1;
	}

	private static boolean isDir(String name) {
		int l = name.length();
		return l > 0 && name.charAt(l - 1) == '/';
	}

	private static int order(String a, String b) {
		int d = (isDir(b) ? 1 : 0) - (isDir(a) ? 1 : 0);
		return d == 0 ? a.compareTo(b) : d;
	}

	private void filter(String text) {
		text = text.toLowerCase();
		filtered.clear();
		for (String n : names)
			if (n.toLowerCase().startsWith(text))
				filtered.add(n);
		sel = -1;
	}

	private void exit(File file) {
		Main.GUI.remove(Main.GUI.size() - 1);
		Main.lockFocus(null);
		result.accept(file);
	}

	@Override
	public void redraw() {
		addSel(0, 0, 64, 3, FG_WHITE | BG_GRAY_D);
		addSel(0, 3, 64, 61, FG_WHITE | BG_GRAY_D);
		addSel(0, sel * 2 + 5, 64, 2, FG_GREEN_L | BG_GRAY_D);
		text.redraw(2, 13, 4, 6, FG_YELLOW_L | BG_TRANSP);
		print(title + dir, FG_YELLOW_L | BG_TRANSP, 2, 3, 4, 6);
		int y = 13;
		for (String name : filtered)
			print(name, (isDir(name) ? FG_BLUE_L : FG_WHITE) | BG_TRANSP, 2, y += 8, 4, 6);
		
		float scaleX = 1F / 64F;
		float scaleY = scaleX * (float)WIDTH / (float)HEIGHT;
		float ofsX = -0.5F, ofsY = scaleY * 32F;
		drawSel(ofsX, ofsY, scaleX, -scaleY, 0F, 0.25F);
		drawText(ofsX, ofsY, scaleX * 0.25F, scaleY * -0.25F);
	}

	@Override
	public boolean onMouseMove(double mx, double my) {
		return true;
	}

	@Override
	public void onKeyInput(int key, int scancode, int action, int mods) {
		if (action == GLFW_RELEASE || sel < 0 && text.onKeyInput(key, mods)) return;
		switch(key) {
		default:
			if (sel >= 0) text.onKeyInput(key, mods);
			return;
		case GLFW_KEY_UP:
			if (--sel < -1) sel = filtered.size() - 1;
			break;
		case GLFW_KEY_DOWN:
			if (++sel >= filtered.size()) sel = -1;
			break;
		case GLFW_KEY_DELETE:
			//delete dir / file
			break;
		case GLFW_KEY_TAB:
			if (sel < 0) {
				if (filtered.size() == 0) return;
				int l0 = text.get().length();
				String tab = filtered.get(0);
				for (String name : filtered)
					for (int i = l0, l = min(tab.length(), name.length()); i < l; i++)
						if (tab.charAt(i) != name.charAt(i)) {
							tab = name.substring(0, i);
							break;
						}
				text.set(tab, -1);
			} else {
				String t = filtered.get(sel);
				filtered.clear();
				filtered.add(t);
				text.set(t, -1);
				sel = -1;
			}
			break;
		case GLFW_KEY_ENTER:
			enter(sel < 0 ? text.get() : filtered.get(sel));
			break;
		case GLFW_KEY_ESCAPE:
			exit(null);
			break;
		}
		Main.refresh(0);
	}

	@Override
	public void onCharInput(int cp) {
		if (cp != '\t') text.onCharInput(cp);
	}

}
