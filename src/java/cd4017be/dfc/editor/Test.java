package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.ICONS;
import static cd4017be.dfc.editor.Shaders.*;
import cd4017be.dfc.editor.gui.*;
import cd4017be.dfc.graphics.SpriteModel;


/**
 * 
 * @author CD4017BE */
public class Test extends GuiGroup {

	String text = "";

	public Test(GuiGroup parent) {
		super(parent, 2);
		parent.add(this);
		add(new Label("Test", 0, 0, 2, FG_RED_L));
		SpriteModel bm = ICONS.get("/textures/button.tga");
		SpriteModel pm = ICONS.get("/textures/buttonPress.tga");
		SpriteModel tm = ICONS.get("/textures/textfield.tga");
		add(new Button(this, "OK", 0, 2, 6, 3, bm, pm, FG_GREEN_L, b -> {}));
		add(new TextField(this, 0, 5, 6, 3, tm, FG_YELLOW_L, () -> text, t -> text = t, () -> {}));
	}

	@Override
	public void onResize(long window, int w, int h) {
		scaleCentered(w, h, 24, 32);
	}

}
