package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Shaders.*;
import java.util.Arrays;
import cd4017be.dfc.editor.gui.Button;
import cd4017be.dfc.editor.gui.Button.ButtonAction;
import cd4017be.dfc.editor.gui.GuiGroup;
import cd4017be.dfc.editor.gui.HoverInfo;
import cd4017be.dfc.editor.gui.Label;
import cd4017be.dfc.editor.gui.TextField;
import cd4017be.dfc.graphics.SpriteModel;

/**
 * @author cd4017be */
public class BlockDefEditor extends GuiGroup {

	static final String VAR = "var", FIX = "fix";
	static final ButtonAction VAR_BTN = (b, mb) -> b.text(b.text == FIX ? VAR : FIX);
	static final SpriteModel MODEL = Main.ICONS.get("/cd4017be/dfc/modules/core/icons/const.tga");

	final Label id, ioL[] = new Label[6];
	final TextField name, type, model;
	final Button ioVar[] = new Button[9];
	final int lastIH, lastL;
	String[][] ios = new String[3][];

	public BlockDefEditor(GuiGroup parent) {
		super(parent, 2);
		parent.add(this);
		infos.add(new HoverInfo(0, 0, 128, 8, "The block ID uniquely identifies a block within its module and determines the file name for its circuit."));
		infos.add(new HoverInfo(0, 12, 128, 20, "The name displayed in block palettes and tool-tips."));
		infos.add(new HoverInfo(0, 24, 128, 32, "How the block is treated in circuits."));
		infos.add(new HoverInfo(0, 36, 128, 44, "The model that defines icon and pin layout."));
		infos.add(new HoverInfo(0, 48, 128, 56, "The numbers of input pins, output pins and text arguments. All elements must be given a signal name and the last element of each type can be set to repeat when the block is expanded."));
		new Label(this).text("Block ID:").pos(0, 0, 2).color(FG_WHITE);
		new Label(this).text("Name:").pos(0, 3, 2);
		new Label(this).text("Type:").pos(0, 6, 2);
		new Label(this).text("Model:").pos(0, 9, 2);
		this.id = new Label(this).color(FG_WHITE);
		this.name = new TextField(this).pos(8, 3, 24, 2).model(MODEL);
		this.type = new TextField(this).pos(8, 6, 24, 2).model(MODEL);
		this.model = new TextField(this).pos(8, 9, 24, 2).model(MODEL);
		makeIO(0, "Inputs:");
		makeIO(1, "Outputs:");
		makeIO(2, "Arguments:");
		this.lastIH = inputHandlers.size();
		this.lastL = drawables.size();
		init("test", "Placeholder", "macro", "none", false, false, false, new String[] {}, new String[] {}, new String[] {});
	}

	private void makeIO(int i, String name) {
		ioL[i] = new Label(this).text(name);
		ioL[i + 3] = new Label(this);
		ioVar[i] = new Button(this).model(MODEL).color(FG_BLUE_XL).action(VAR_BTN);
		ioVar[i + 3] = new Button(this).text("+").model(MODEL).color(FG_GREEN_L).action((b, mb) -> changeIOCount(i, 1));
		ioVar[i + 6] = new Button(this).text("-").model(MODEL).color(FG_RED_L).action((b, mb) -> changeIOCount(i, -1));
	}

	public void init(
		String id, String name, String type, String model,
		boolean varIn, boolean varOut, boolean varArg,
		String[] ins, String[] outs, String[] args
	) {
		this.id.text(id).pos(42 - id.length() >> 1, 0, 2);
		this.name.text(name);
		this.type.text(type);
		this.model.text(model);
		this.ioVar[0].text(varIn ? VAR : FIX);
		this.ioVar[1].text(varOut ? VAR : FIX);
		this.ioVar[2].text(varArg ? VAR : FIX);
		this.ios[0] = ins;
		this.ios[1] = outs;
		this.ios[2] = args;
		updateIOs();
	}

	private void updateIOs() {
		inputHandlers.subList(lastIH, inputHandlers.size()).clear();
		drawables.subList(lastL, drawables.size()).clear();
		for (int y = 12, i = 0; i < 3; i++) {
			String[] names = ios[i];
			int n = names.length;
			ioL[i].pos(0, y, 2);
			ioL[i + 3].pos(19, y, 2).text(Integer.toString(n));
			ioVar[i].pos(28, y, 4, 2);
			ioVar[i + 3].pos(22, y, 2, 2);
			ioVar[i + 6].pos(16, y, 2, 2);
			y += 3;
			for (int j = 0; j < n; j++, y += 3) {
				int jj = j;
				new TextField(this).text(names[j]).model(MODEL).pos(8, y, 24, 2)
				.action((tf, finish) -> {if (finish) names[jj] = tf.text;});
			}
		}
	}

	private void changeIOCount(int i, int d) {
		Label l = ioL[i + 3];
		int n = Integer.parseInt(l.text) + d;
		if (n < 0) return;
		String[] arr = ios[i] = Arrays.copyOf(ios[i], n);
		if (d > 0) arr[n - 1] = (i == 0 ? "in" : i == 1 ? "out" : "arg") + (n - 1);
		updateIOs();
	}

	@Override
	public void onResize(long window, int w, int h) {
		scale = w < 640 ? 1 : 2;
		x0 = w - 144 * scale;
		x1 = w - 16 * scale;
		y0 = 0;
		y1 = h;
	}

}
