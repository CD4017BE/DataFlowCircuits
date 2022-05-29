package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.CircuitEditor.FILE;
import static cd4017be.dfc.editor.CircuitEditor.withSuffix;
import static cd4017be.dfc.editor.Main.*;
import static cd4017be.dfc.editor.Shaders.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.lwjgl.glfw.GLFW.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntConsumer;

import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL32C.*;

import cd4017be.dfc.compiler.IntrinsicEvaluators;
import cd4017be.dfc.lang.*;
import cd4017be.util.AtlasSprite;
import cd4017be.util.GLUtils;

/**
 * @author CD4017BE */
public class MacroEdit implements IGuiSection {

	static final String LEN = "length=";
	static final File EDIT_DOC, EDIT_ICON;
	static final String[] //TODO replace hard-coded program paths
	TEXT_EDITOR = {"C:\\Program Files\\Notepad++\\notepad++.exe", null},
	IMAGE_EDITOR = {"C:\\Program Files\\paint.net\\paintdotnet.exe", null};
	static {
		File doc = null, icon = null; try {
			(doc = File.createTempFile("dfc", ".txt")).deleteOnExit();
			TEXT_EDITOR[TEXT_EDITOR.length - 1] = doc.getAbsolutePath();
			(icon = File.createTempFile("dfc", ".tga")).deleteOnExit();
			IMAGE_EDITOR[IMAGE_EDITOR.length - 1] = icon.getAbsolutePath();
		} catch(IOException e) {
			e.printStackTrace();
		}
		EDIT_DOC = doc;
		EDIT_ICON = icon;
	}

	final ArrayList<Box> boxes = new ArrayList<>();
	final BlockIcons icons;
	final TextField edit = new TextField(this::editText, 0, 0);
	final int traceVAO, blockVAO, traceBuf, blockBuf;
	final int boxDesc, boxArg, boxPos, boxLen, boxN;
	int mx, my, sel = -1;
	boolean selText = false;
	File curFile;
	
	BlockDef def;
	String longDesc;
	boolean hasArg;
	int outCount, inCount, mod;
	byte textX, textY, textL;
	byte[] pins = new byte[0];
	long editDoc, editIcon;

	public MacroEdit(BlockIcons icons) {
		this.icons = icons;
		this.traceVAO = genTraceVAO(traceBuf = glGenBuffers());
		glBufferData(GL_ARRAY_BUFFER, 26 * TRACE_STRIDE, GL_STREAM_DRAW);
		glUseProgram(traceP);
		glUniform2f(trace_lineSize, 0.125F, 0.5F);
		checkGLErrors();
		this.blockVAO = genBlockVAO(blockBuf = glGenBuffers());
		glBufferData(GL_ARRAY_BUFFER, BLOCK_STRIDE, GL_STATIC_DRAW);
		Main.GUI.add(this);
		Main.lockFocus(this);
		Main.refresh(0);
		boxes.add(new Box(1, 61, 14, 2).text("Open Block", 0xffffffff).click(this::open, 0xffc0c040, 0xffffff80));
		boxes.add(new Box(17, 61, 14, 2).text("New Block", 0xffffffff).click(this::newBlock, 0xffc0c040, 0xffffff80));
		boxes.add(new Box(33, 61, 14, 2).text("Edit Circuit", 0xffffffff).click(this::editCircuit, 0xffc0c040, 0xffffff80));
		boxes.add(new Box(49, 61, 14, 2).text("Save Block", 0xffffffff).click(this::save, 0xff40c040, 0xff80ff80));
		boxes.add(new Box(1, 1, 12, 2).text("Description:", 0xffffffff));
		boxDesc = boxes.size();
		boxes.add(new Box(1, 3, 62, 2).text("", 0xffffff80).click(clickSel(true), 0xffc0c0c0, 0xffffffff));
		boxes.add(new Box(42, 1, 21, 2).text("Edit documentation", 0xffffffff).click(this::editDoc, 0xffc0c040, 0xffffff80));
		boxes.add(new Box(26, 11, 12, 2).text("Edit icon", 0xffffffff).click(this::editIcon, 0xffc0c040, 0xffffff80));
		boxes.add(new Box(1, 6, 16, 2).text("Argument macros:", 0xffffffff));
		boxLen = boxes.size();
		boxes.add(new Box(42, 6, 10, 2).text("", 0xffffff80).click(clickSel(true), 0xffc0c0c0, 0xffffffff));
		boxPos = boxes.size();
		boxes.add(new Box(53, 6, 10, 2).text("position", 0xffffff80).click(clickSel(false), 0xff4040ff, 0xffc0c0ff));
		boxArg = boxes.size();
		boxes.add(new Box(1, 8, 62, 2).text("", 0xffffff80).click(clickSel(true), 0xffc0c0c0, 0xffffffff));
		boxes.add(new Box(48, 11, 7, 2).text("Output:", 0xffffffff));
		boxes.add(new Box(59, 11, 2, 2).click(this::addOut, 0xff80ff80, 0xffc0ffc0).text("+", 0xff80ff80));
		boxes.add(new Box(61, 11, 2, 2).click(this::remOut, 0xffff4040, 0xffffc0c0).text("-", 0xffff8080));
		boxes.add(new Box(1, 11, 7, 2).text("Inputs:", 0xffffffff));
		boxes.add(new Box(12, 11, 2, 2).click(this::addIn, 0xff80ff80, 0xffc0ffc0).text("+", 0xff80ff80));
		boxes.add(new Box(14, 11, 2, 2).click(this::remIn, 0xffff4040, 0xffffc0c0).text("-", 0xffff8080));
		boxN = boxes.size();
		allocSelBuf(boxN);
		setDef(icons.placeholder);
	}

	private void setDef(BlockDef def) {
		this.def = def;
		outCount = def.outCount;
		inCount = def.inCount;
		pins = Arrays.copyOfRange(def.pins, 0, def.ios() << 1);
		if (hasArg = def.hasArg) {
			int l = def.pins.length - 3;
			textX = def.pins[l++];
			textY = def.pins[l++];
			textL = def.pins[l++];
		} else textX = textY = textL = 0;
		boxes.subList(boxN, boxes.size()).clear();
		boxes.get(boxDesc).text = def.shortDesc;
		boxes.get(boxArg).text = hasArg ? def.ioNames[def.ios()] : "";
		boxes.get(boxLen).text = LEN + textL;
		for (int i = 0; i < def.ios(); i++) {
			addPinName(def.ioNames[i], i < outCount);
			updateTrace(i * 2);
		}
		glBindBuffer(GL_ARRAY_BUFFER, blockBuf);
		glBufferSubData(GL_ARRAY_BUFFER, 0, new short[] {0, 0, 0, (short)def.icon.id});
		longDesc = def.longDesc;
		mod = 0;
		editDoc = editIcon = 0;
	}

	private IntConsumer clickSel(boolean text) {
		final int i = boxes.size();
		return text ? b -> {
			edit.set(boxes.get(i).text, -1);
			selText = true;
			sel = i;
		} : b -> sel = i;
	}

	private void addPinName(String name, boolean out) {
		int l = boxes.size();
		int i = (l - boxN >> 1) - (out ? 0 : outCount);
		int j = min(l, boxN + (outCount + (out ? 0 : inCount) << 1));
		if (name == null) name = out ? "out" : "in" + i;
		i = i * 2 + 13;
		boxes.add(j,
			new Box(out ? 48 : 1, i, 15, 2)
			.click(clickSel(true), 0xffc0c0c0, 0xffffffff)
			.text(name, 0xffffff80)
		);
		boxes.add(j + 1,
			new Box(out ? 45 : 17, i, 2, 2)
			.click(clickSel(false), 0xff4040ff, 0xffc0c0ff)
		);
		allocSelBuf(boxes.size());
	}

	@Override
	public void redraw() {
		if (editDoc != 0 && editDoc < (editDoc = EDIT_DOC.lastModified()))
			reloadDoc();
		if (editIcon != 0 && editIcon < (editIcon = EDIT_ICON.lastModified()))
			reloadIcon();
		float scaleY = min(1F, (float)WIDTH / (float)HEIGHT) / 32F;
		float scaleX = min(1F, (float)HEIGHT / (float)WIDTH) / 32F;
		float ofsY = scaleY * 32F, ofsX = scaleX * -32F;
		
		//addSel(0, 0, 64, 64, 0xffffffff);
		//drawSel(ofsX, ofsY, scaleX, -scaleY, 0F, 0.25F, 0xff202020);
		
		float[] mat = new float[] {
			scaleX * 2F,  0, 0, 0,
			0, scaleY * -2F, 0, 0,
			ofsX + scaleX * 20, ofsY - scaleY * 13, 0, 1F
		};
		icons.update();
		icons.bind();
		glUseProgram(blockP);
		glUniformMatrix3x4fv(block_transform, false, mat);
		glBindVertexArray(blockVAO);
		glDrawArrays(GL_POINTS, 0, 1);
		checkGLErrors();
		mat[0] = scaleX; mat[5] = -scaleY;
		glUseProgram(traceP);
		glUniformMatrix3x4fv(trace_transform, false, mat);
		glUniform2f(trace_lineSize, 0.125F, 0.5F);
		glBindVertexArray(traceVAO);
		glDrawArrays(GL_LINES, 0, pins.length);
		checkGLErrors();
		if (hasArg)
			addSel(textX + 20, textY + 13, textL == 0 ? 1 : textL << 1, 4, 0xffffff80);
		for(Box box : boxes)
			box.drawFrame(mx, my);
		drawSel(ofsX, ofsY, scaleX, -scaleY, 0F, 0.5F, 0x00000000);
		startText();
		for(Box box : boxes)
			box.drawText(ofsX, ofsY, scaleX, scaleY);
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
		boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0;
		switch(key) {
		case GLFW_KEY_S:
			if (ctrl) save((mods & GLFW_MOD_SHIFT) != 0 ? GLFW_MOUSE_BUTTON_RIGHT : GLFW_MOUSE_BUTTON_LEFT);
			break;
		case GLFW_KEY_O:
			if (ctrl) open(0);
			break;
		case GLFW_KEY_N:
			if (ctrl) newBlock(0);
			break;
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
		float scaleY = min(1F, (float)WIDTH / (float)HEIGHT) / 32F;
		float scaleX = min(1F, (float)HEIGHT / (float)WIDTH) / 32F;
		float ofsY = scaleY * -32F, ofsX = scaleX * -32F;
		if (
			this.mx == (this.mx = (int)Math.floor((mx - ofsX) / scaleX))
			& this.my == (this.my = (int)Math.floor((my - ofsY) / scaleY))
		) return true;
		if (!selText) {
			if (sel == boxPos) {
				hasArg = true;
				int x = this.mx - 20, y = this.my - 13;
				textX = (byte)(x & 15);
				textY = (byte)(y & 15);
			} else if (sel >= boxN && sel < boxes.size()) {
				int x = this.mx - 19 >> 1, y = this.my - 11 >> 1;
				int i = sel - boxN & -2;
				pins[i  ] = (byte)x;
				pins[i+1] = (byte)y;
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
			if (sel == boxLen) {
				String s = edit.get();
				for (int i = 0; i < s.length(); i++)
					if (i >= LEN.length() || s.charAt(i) != LEN.charAt(i)) {
						try {textL = (byte)(Integer.parseInt(s.substring(i)) & 15);}
						catch(NumberFormatException e) {}
						break;
					}
				boxes.get(boxLen).text = LEN + textL;
			}
			selText = false;
			sel = -1;
		} else if (sel >= 0) {
			if (sel == boxPos && my == 7) hasArg = false;
			mod |= 1;
			sel = -1;
			Main.refresh(0);
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
		int x = pins[i], y = pins[i + 1];
		if (x < 0) x += def.icon.w + 1;
		if (y < 0) y += def.icon.h + 1;
		boolean out = i < outCount * 2;
		int y0 = (out ? i : i - outCount * 2) + 1;
		glBindBuffer(GL_ARRAY_BUFFER, traceBuf);
		glBufferSubData(GL_ARRAY_BUFFER, i * TRACE_STRIDE, new short[] {
			(short)(out ? 26 : -2), (short)y0, 12,
			(short)(x * 2), (short)(y * 2), 12
		});
	}

	private void addOut(int button) {
		if (outCount >= 1) return;
		addPinName(null, true);
		byte[] arr = new byte[pins.length + 2];
		int n = outCount++ << 1;
		System.arraycopy(pins, 0, arr, 0, n);
		System.arraycopy(pins, n, arr, n + 2, inCount << 1);
		pins = arr;
		for (int i = n; i < arr.length; i+=2)
			updateTrace(i);
		boxes.get(boxes.size() - 2).onClick.accept(button);
		mod |= 1;
	}

	private void addIn(int button) {
		addPinName(null, false);
		pins = Arrays.copyOf(pins, outCount + ++inCount << 1);
		updateTrace(pins.length - 2);
		boxes.get(boxes.size() - 2).onClick.accept(button);
		mod |= 1;
	}

	private void remOut(int button) {
		if (outCount <= 0) return;
		int i = boxN + outCount * 2;
		boxes.subList(i - 2, i).clear();
		byte[] arr = new byte[pins.length - 2];
		int n = --outCount << 1;
		System.arraycopy(pins, 0, arr, 0, n);
		System.arraycopy(pins, n + 2, arr, n, inCount << 1);
		pins = arr;
		for (i = n; i < arr.length; i+=2)
			updateTrace(i);
		mod |= 1;
	}

	private void remIn(int button) {
		if (inCount <= 0) return;
		int l = boxes.size();
		boxes.subList(l - 2, l).clear();
		pins = Arrays.copyOf(pins, outCount + --inCount << 1);
		mod |= 1;
	}

	private void editText(String s) {
		if (sel < 0 || sel >= boxes.size()) return;
		Box box = boxes.get(sel);
		box.text = s;
		mod |= sel == boxDesc ? 2 : 1;
	}

	private void editDoc(int button) {
		if (EDIT_DOC == null) return;
		try (FileWriter fw = new FileWriter(EDIT_DOC, UTF_8, false)) {
			fw.append(def.longDesc);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		editDoc = EDIT_DOC.lastModified();
		try {
			Runtime.getRuntime().exec(TEXT_EDITOR);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void reloadDoc() {
		try {
			longDesc = Files.readString(EDIT_DOC.toPath(), UTF_8);
			mod |= 2;
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void editIcon(int button) {
		if (EDIT_ICON == null) return;
		try (
			FileOutputStream out = new FileOutputStream(EDIT_ICON);
			MemoryStack ms = MemoryStack.stackPush()
		) {
			ByteBuffer buf = icons.getData(ms);
			AtlasSprite icon = def.icon;
			int scan = buf.getShort();
			GLUtils.writeTGA16(out,
				buf.position(buf.position() + icon.x * 8 + icon.y * 4 * scan),
				scan, icon.w << 2, icon.h << 2
			);
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		editIcon = EDIT_ICON.lastModified();
		try {
			Runtime.getRuntime().exec(IMAGE_EDITOR);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void reloadIcon() {
		try (
			FileInputStream in = new FileInputStream(EDIT_ICON);
			MemoryStack ms = MemoryStack.stackPush()
		) {
			ByteBuffer img = GLUtils.readTGA(in, ms);
			if (def == icons.placeholder)
				def = new BlockDef(def.name, outCount, inCount, hasArg);
			else if (def.icon == icons.placeholder.icon)
				def.icon = null;
			int id = icons.load(img, def).id;
			mod |= 4;
			glBindBuffer(GL_ARRAY_BUFFER, blockBuf);
			glBufferSubData(GL_ARRAY_BUFFER, 0, new short[] {0, 0, 0, (short)id});
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void open(int button) {
		new FileBrowser(
			curFile == null ? FILE : curFile.getParentFile(),
			"Open ", CircuitFile.FILTER, this::load
		);
	}

	private void newBlock(int button) {
		curFile = null;
		setDef(icons.placeholder);
	}

	private void editCircuit(int button) {
		Main.GUI.remove(Main.GUI.size() - 1);
		Main.lockFocus(null);
		BlockRegistry reg = new BlockRegistry("./src/dfc/std", "./test");
		IntrinsicEvaluators.register(reg);
		Palette pal = new Palette(icons, reg);
		Main.GUI.add(new CircuitEditor(this, pal, reg));
		Main.GUI.add(pal);
		Main.refresh(0);
	}

	private void save(int button) {
		if (curFile != null) save(curFile);
		else new FileBrowser(FILE, "Save as ", CircuitFile.FILTER, this::save);
	}

	private void load(File file) {
		if (file == null) return;
		curFile = file = withSuffix(file, ".dfc");
		String name = file.getName();
		setTitle(name);
		try (CircuitFile cf = new CircuitFile(file.toPath(), false)) {
			BlockDef def = cf.readInterface(name.substring(0, name.length() - 4));
			cf.readDescription(def);
			icons.load(cf.readIcon(), def);
			setDef(def);
		} catch(IOException e) {
			e.printStackTrace();
		}
		refresh(0);
	}

	public BlockDef getDef() {
		BlockDef def = this.def;
		if (inCount != def.inCount || outCount != def.outCount || hasArg != def.hasArg) {
			def = new BlockDef(def.name, outCount, inCount, hasArg);
			def.icon = this.def.icon;
			this.def = def;
		}
		int l = pins.length;
		System.arraycopy(pins, 0, def.pins, 0, l);
		for (int i = 0, j = boxN; j < boxes.size(); i++, j+=2)
			def.ioNames[i] = boxes.get(j).text;
		if (hasArg) {
			def.pins[l] = textX;
			def.pins[l + 1] = textY;
			def.pins[l + 2] = textL;
			def.ioNames[def.ios()] = boxes.get(boxArg).text;
		}
		def.shortDesc = boxes.get(boxDesc).text;
		def.longDesc = longDesc;
		return def;
	}

	private void save(File file) {
		if (file == null) return;
		curFile = file = withSuffix(file, ".dfc");
		setTitle(file.getName());
		getDef();
		try (CircuitFile cf = new CircuitFile(file.toPath(), true)) {
			if ((mod & 1) != 0) cf.clear(CircuitFile.INTERFACE);
			if ((mod & 2) != 0) cf.clear(CircuitFile.DESCRIPTION);
			if ((mod & 4) != 0)
				try (MemoryStack ms = MemoryStack.stackPush()) {
					ByteBuffer buf = icons.getData(ms);
					int scan = buf.getShort();
					AtlasSprite icon = def.icon;
					buf.position(2 + icon.x * 8 + icon.y * 4 * scan);
					cf.writeIcon(buf, scan, icon.w << 2, icon.h << 2);
				}
			if ((mod & 1) != 0) cf.writeInterface(def);
			if ((mod & 2) != 0) cf.writeDescription(def);
			cf.writeHeader();
			mod = 0;
		} catch (IOException e) {
			e.printStackTrace();
		}
		refresh(0);
	}

}
