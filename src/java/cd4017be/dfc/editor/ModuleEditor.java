package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.*;

import java.util.Arrays;

import cd4017be.dfc.editor.gui.*;
import cd4017be.dfc.lang.Module;


/**
 * 
 * @author CD4017BE */
public class ModuleEditor extends GuiGroup {

	final BlockDefEditor defEdit;
	final Label path, elemL[];
	final TextField[] elemNew;
	final Button[] elemAdd;
	final int lastIH, lastL;
	final String[][] elements;
	Module module;

	public ModuleEditor(GuiGroup parent) {
		super(parent, 2);
		parent.add(this);
		this.defEdit = new BlockDefEditor(parent);
		new Label(this).text("Module:").pos(0, 0, 12).color(FG_WHITE);
		this.path = new Label(this).color(FG_WHITE);
		this.elemL = new Label[] {
			new Label(this).color(FG_GRAY_L).text("Imports:"),
			new Label(this).color(FG_GRAY_L).text("Blocks:"),
			new Label(this).color(FG_GRAY_L).text("Types:"),
		};
		this.elemNew = new TextField[] {
			new TextField(this),
			new TextField(this),
			new TextField(this),
		};
		this.elemAdd = new Button[] {
			new Button(this).color(FG_GREEN_L).text("+").action(this::addElement),
			new Button(this).color(FG_GREEN_L).text("+").action(this::addElement),
			new Button(this).color(FG_GREEN_L).text("+").action(this::addElement),
		};
		this.elements = new String[3][];
		this.lastIH = inputHandlers.size();
		this.lastL = drawables.size();
		init("core", new String[] {}, new String[] {}, new String[] {});
	}

	public void init(Module module, String[] imports, String[] blocks, String[] types) {
		this.module = module;
		this.path.text(module.name).pos(84 - module.name.length() * 2, 0, 12);
		this.elements[0] = imports;
		this.elements[1] = blocks;
		this.elements[2] = types;
		updateElements();
	}

	private void updateElements() {
		chop(lastL, lastIH);
		int y = 0;
		for (int i = 0; i < 3; i++) {
			elemL[i].pos(0, y += 12, 12);
			elemNew[i].pos(0, y += 12, 128, 12);
			elemAdd[i].pos(148, y, 12, 12);
			String[] elem = elements[i];
			for (int j = 0; j < elem.length; j++) {
				final int i_ = i, j_ = j;
				new Button(this).color(FG_BLUE_XL).text(elem[j]).pos(0, y += 12, 128, 12).action((b, mb) -> editElement(i_, j_));
				new Button(this).color(FG_RED_L).text("-").pos(148, y, 12, 12).action((b, mb) -> remElement(i_, j_));
			}
		}
	}

	@Override
	public void onResize(long window, int w, int h) {
		scale = w < 640 ? 1 : w < 1280 ? 2 : 4;
		x0 = (w - 320 * scale) / 4;
		x1 = x0 + 160 * scale;
		y0 = 0;
		y1 = h;
	}

	private void addElement(Button b, int mb) {
		int i;
		for (i = 0; i < elemAdd.length; i++)
			if (elemAdd[i] == b) break;
		String name = elemNew[i].text;
		elemNew[i].text("");
		if (name.isBlank()) return;
		String[] arr = elements[i];
		int l = arr.length;
		arr = Arrays.copyOf(arr, l + 1);
		arr[l] = name;
		elements[i] = arr;
		updateElements();
		editElement(i, l);
	}

	private void editElement(int i, int j) {
		String[] arr = elements[i];
		switch(i) {
		case 0:
		case 1:
			defEdit.init(arr[j], "name", "type", "model", false, false, false, new String[0], new String[0], new String[0]);
			break;
		case 2:
		}
	}

	private void remElement(int i, int j) {
		String[] arr = elements[i];
		String[] narr = new String[arr.length - 1];
		System.arraycopy(arr, 0, narr, 0, j);
		System.arraycopy(arr, j + 1, narr, j, narr.length - j);
		elements[i] = narr;
		updateElements();
	}

}
