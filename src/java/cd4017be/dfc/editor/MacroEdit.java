package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static org.lwjgl.glfw.GLFW.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntConsumer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL21C.glUniformMatrix3x4fv;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL32C.*;

import cd4017be.dfc.lang.BlockDef;

/**
 * @author CD4017BE */
public class MacroEdit implements IGuiSection {

	static final String LEN = "length=";

	ArrayList<Box> boxes = new ArrayList<>();
	BlockIcons icons;
	BlockDef def;
	TextField edit = new TextField(this::editText, 0, 0);
	final int traceVAO, blockVAO, traceBuf, blockBuf, boxN;
	int mx, my, sel = -1;
	boolean selText = false;

	public MacroEdit(BlockIcons icons, BlockDef def) {
		this.icons = icons;
		this.def = def;
		def.icon = icons.get("main").icon;//TODO temporary
		this.traceVAO = genTraceVAO(traceBuf = glGenBuffers());
		glBufferData(GL_ARRAY_BUFFER, 26 * TRACE_STRIDE, GL_STREAM_DRAW);
		this.blockVAO = genBlockVAO(blockBuf = glGenBuffers());
		glBufferData(GL_ARRAY_BUFFER, new short[] {0, 0, 0, (short)def.icon.id}, GL_STATIC_DRAW);
		Main.GUI.add(this);
		Main.lockFocus(this);
		Main.refresh(0);
		boxes.add(new Box(1, 0, 7, 2).text("Output:", 0xffffffff));
		boxes.add(new Box(1, 4, 7, 2).text("Inputs:", 0xffffffff));
		boxes.add(new Box(12, 4, 2, 2).click(this::add, 0xff80ff80, 0xffc0ffc0).text("+", 0xff80ff80));
		boxes.add(new Box(14, 4, 2, 2).click(this::rem, 0xffff4040, 0xffffc0c0).text("-", 0xffff8080));
		boxes.add(new Box(1, 30, 16, 2).text("Argument macros:", 0xffffffff));
		boxes.add(new Box(42, 30, 10, 2).text(LEN + def.textL0, 0xffffff80).click(clickSel(true), 0xffc0c0c0, 0xffffffff));
		boxes.add(new Box(53, 30, 10, 2).text("position", 0xffffff80).click(clickSel(false), 0xff4040ff, 0xffc0c0ff));
		boxes.add(new Box(1, 32, 62, 2).text(def.textMacro == null ? "" : def.textMacro, 0xffffff80).click(clickSel(true), 0xffc0c0c0, 0xffffffff));
		boxes.add(new Box(1, 34, 14, 2).text("Documentation:", 0xffffffff));
		boxes.add(new Box(1, 36, 62, 27).text(def.description, 0xffffff80).click(clickSel(true), 0xffc0c0c0, 0xffffffff));
		boxN = boxes.size();
		for (String name : def.ioNames)
			addPinName(name);
		for (int i = 0; i < def.ios(); i++)
			updateTrace(i);
	}

	private IntConsumer clickSel(boolean text) {
		final int i = boxes.size();
		return text ? b -> {
			edit.set(boxes.get(i).text, -1);
			selText = true;
			sel = i;
		} : b -> sel = i;
	}

	private void addPinName(String name) {
		int l = boxes.size(), i = (l - boxN >> 1) - 1;
		if (name == null) name = "in" + i;
		if (i >= 0) i++;
		i = i * 2 + 4;
		boxes.add(
			new Box(1, i, 15, 2)
			.click(clickSel(true), 0xffc0c0c0, 0xffffffff)
			.text(name, 0xffffff80)
		);
		boxes.add(new Box(17, i, 2, 2).click(clickSel(false), 0xff4040ff, 0xffc0c0ff));
		allocSelBuf(boxes.size());
	}

	@Override
	public void redraw() {
		// load image
		float scaleX = 1F / 64F;
		float scaleY = scaleX * (float)WIDTH / (float)HEIGHT;
		float ofsY = scaleY * 32F, ofsX = -0.5F;
		
		addSel(0, 0, 64, 64, 0xffffffff);
		drawSel(ofsX, ofsY, scaleX, -scaleY, 0F, 0.25F, 0xff202020);
		
		float[] mat = new float[] {
			scaleX * 2F,  0, 0, 0,
			0, scaleY * -2F, 0, 0,
			ofsX + scaleX * 20, ofsY - scaleY * 2, 0, 1F
		};
		icons.bind();
		glUseProgram(blockP);
		glUniformMatrix3x4fv(block_transform, false, mat);
		glBindVertexArray(blockVAO);
		glDrawArrays(GL_POINTS, 0, 1);
		checkGLErrors();
		mat[0] = scaleX; mat[5] = -scaleY;
		glUseProgram(traceP);
		glUniformMatrix3x4fv(trace_transform, false, mat);
		glBindVertexArray(traceVAO);
		glDrawArrays(GL_LINES, 0, boxes.size() - boxN);
		checkGLErrors();
		if (def.textMacro != null)
			addSel(def.textX + 20, def.textY + 2, def.textL0 == 0 ? 1 : def.textL0 << 1, 4, 0xffffff80);
		
		for(Box box : boxes) box.drawFrame(mx, my);
		drawSel(ofsX, ofsY, scaleX, -scaleY, 0F, 0.25F, 0x00000000);
		startText();
		for(Box box : boxes) box.drawText(ofsX, ofsY, scaleX, scaleY);
		if (selText && sel >= 0 && sel < boxes.size()) {
			Box box = boxes.get(sel);
			int w = box.x1 - box.x0, h = box.y1 - box.y0, l = box.text.length();
			edit.redraw(
				ofsX + scaleX * (box.x0 + max((w - l) * 0.5F, 0)),
				ofsY - scaleY * (box.y0 + max(h * 0.5F - ((l-1)/w + 1) * 0.75F, 0)),
				scaleX, scaleY * -1.5F,
				w, box.tc, box.tc, 0xffff8080
			);
		}
	}

	@Override
	public void onKeyInput(int key, int scancode, int action, int mods) { 
		if (action == GLFW_RELEASE || selText && edit.onKeyInput(key, mods)) return;
		switch(key) {
		case GLFW_KEY_ESCAPE:
			exit();
			return;
		}
		Main.refresh(0);
	}

	private void exit() {
		Main.GUI.remove(Main.GUI.size() - 1);
		Main.lockFocus(null);
		Main.refresh(0);
		glDeleteVertexArrays(blockVAO);
		glDeleteVertexArrays(traceVAO);
		glDeleteBuffers(blockBuf);
		glDeleteBuffers(traceBuf);
	}

	@Override
	public void onCharInput(int cp) {
		if (cp != '\t') edit.onCharInput(cp);
	}

	@Override
	public boolean onMouseMove(double mx, double my) {
		float scaleX = 1F / 64F;
		float scaleY = scaleX * (float)WIDTH / (float)HEIGHT;
		float ofsY = scaleY * -32F, ofsX = -0.5F;
		if (
			this.mx == (this.mx = (int)Math.floor((mx - ofsX) / scaleX))
			& this.my == (this.my = (int)Math.floor((my - ofsY) / scaleY))
		) return true;
		if (!selText) {
			if (sel == 6) {
				int x = this.mx - 20, y = this.my - 2;
				def.textX = (byte)(x & 15);
				def.textY = (byte)(y & 15);
			} else if (sel >= boxN && sel < boxes.size()) {
				int x = this.mx - 19 >> 1, y = this.my - 1 >> 1;
				int i = sel - boxN >> 1;
				def.ports[i*2  ] = (byte)x;
				def.ports[i*2+1] = (byte)y;
				updateTrace(i);
			}
		}
		Main.refresh(0);
		return true;
	}

	@Override
	public void onMouseButton(int button, int action, int mods) {
		if (action == GLFW_RELEASE) return;
		if (selText) {
			String s = edit.get();
			if (sel == 5) {
				for (int i = 0; i < s.length(); i++)
					if (i >= LEN.length() || s.charAt(i) != LEN.charAt(i)) {
						try {def.textL0 = (byte)(Integer.parseInt(s.substring(i)) & 15);}
						catch(NumberFormatException e) {}
						break;
					}
				boxes.get(sel).text = LEN + def.textL0;
			} else if (sel == 7)
				def.textMacro = s.isBlank() ? null : s;
			else if (sel >= boxN && sel < boxes.size())
				def.ioNames[sel - boxN >> 1] = s;
			selText = false;
			sel = -1;
		} else if (sel >= 0) {
			sel = -1;
			return;
		}
		for (Box box : boxes)
			if (box.pointInside(mx, my)) {
				box.onClick.accept(action);
				break;
			}
		Main.refresh(0);
	}

	private void updateTrace(int i) {
		glBindBuffer(GL_ARRAY_BUFFER, traceBuf);
		int x = def.ports[i * 2], y = def.ports[i * 2 + 1];
		if (x < 0) x += def.icon.w + 1;
		if (y < 0) y += def.icon.h + 1;
		glBufferSubData(GL_ARRAY_BUFFER, i * TRACE_STRIDE * 2, new short[] {
			-2, (short)(i == 0 ? 1 : i * 2 + 3), Trace.VOID_COLOR,
			(short)(x * 2), (short)(y * 2), Trace.VOID_COLOR
		});
	}

	private void resizePins() {
		int n = boxes.size() - boxN;
		def.ioNames = Arrays.copyOf(def.ioNames, n >> 1);
		def.ports = Arrays.copyOf(def.ports, n);
	}

	private void add(int button) {
		addPinName(null);
		resizePins();
		int l = boxes.size() - 2;
		updateTrace(l - boxN >> 1);
		boxes.get(l).onClick.accept(button);
	}

	private void rem(int button) {
		int l = boxes.size() - 2;
		if (l > boxN) {
			boxes.remove(l + 1);
			boxes.remove(l);
			resizePins();
		}
	}

	private void editText(String s) {
		if (sel < 0 || sel >= boxes.size()) return;
		Box box = boxes.get(sel);
		box.text = s;
	}

}
