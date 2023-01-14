package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static cd4017be.util.GLUtils.*;
import static org.lwjgl.opengl.GL20C.*;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

import cd4017be.util.AtlasSprite;
import cd4017be.util.VertexArray;
import cd4017be.util.VertexArray.Attribute;

/**Loads and configures all OpenGL shaders used by the program.
 * @author CD4017BE */
public class Shaders {

	private static final int textV = loadShader(GL_VERTEX_SHADER, "/shaders/text_vert.glsl");
	private static final int textF = loadShader(GL_FRAGMENT_SHADER, "/shaders/text_frag.glsl");
	/** text rendering shader */
	public static final int textP = program(textV, textF);
	public static final int TEXT_STRIDE = 8;
	private static final Attribute[] textA = {
		new Attribute(textP, "pos", 2, GL_SHORT, false, 0),
		new Attribute(textP, "charCode", 1, GL_SHORT, false, 4),
		new Attribute(textP, "color", 1, GL_UNSIGNED_BYTE, false, 6),
		new Attribute(textP, "corner", 1, GL_UNSIGNED_BYTE, false, 7)
	};
	/** vec4(w, h, x0, y0): size and offset of tiles in font texture */
	private static final int text_tileSize = glGetUniformLocation(textP, "tileSize");
	/** mat3x3: transformation from character grid to screen coordinates */
	public static final int text_transform = glGetUniformLocation(textP, "transform");

	private static final int blockV = loadShader(GL_VERTEX_SHADER, "/shaders/block_vert.glsl");
	private static final int blockF = loadShader(GL_FRAGMENT_SHADER, "/shaders/block_frag.glsl");
	/** block rendering shader */
	public static final int blockP = program(blockV, blockF);
	public static final int BLOCK_STRIDE = 12, BLOCK_PRIMLEN = BLOCK_STRIDE * 4;
	private static final Attribute[] blockA = {
		new Attribute(blockP, "pos", 2, GL_SHORT, false, 0),
		new Attribute(blockP, "str", 2, GL_SHORT, false, 4),
		new Attribute(blockP, "iconId", 1, GL_SHORT, false, 8),
		new Attribute(blockP, "corner", 1, GL_SHORT, false, 10)
	};
	/** vec3: (block grid xy, icon index) to texture coordinate scale */
	public static final int block_texScale = glGetUniformLocation(blockP, "texScale");
	/** mat3x3: transformation from block grid to screen coordinates */
	public static final int block_transform = glGetUniformLocation(blockP, "transform");

	private static final int traceV = loadShader(GL_VERTEX_SHADER, "/shaders/wire_vert.glsl");
	private static final int traceF = loadShader(GL_FRAGMENT_SHADER, "/shaders/wire_frag.glsl");
	/** trace rendering shader */
	public static final int traceP = program(traceV, traceF);
	public static final int TRACE_STRIDE = 10, TRACE_PRIMLEN = TRACE_STRIDE * 4;
	private static final Attribute[] traceA = {
		new Attribute(traceP, "pos", 4, GL_SHORT, false, 0),
		new Attribute(traceP, "type", 1, GL_UNSIGNED_BYTE, false, 8),
		new Attribute(traceP, "corner", 1, GL_UNSIGNED_BYTE, false, 9)
	};
	/** vec2: trace line thickness in grid coordinates */
	public static final int trace_texScale = glGetUniformLocation(traceP, "texScale");
	/** vec2: trace line thickness in grid coordinates */
	public static final int trace_lineSize = glGetUniformLocation(traceP, "lineSize");
	/** mat3x3: transformation from trace grid to screen coordinates */
	public static final int trace_transform = glGetUniformLocation(traceP, "transform");

	private static final int selV = loadShader(GL_VERTEX_SHADER, "/shaders/sel_vert.glsl");
	private static final int selF = loadShader(GL_FRAGMENT_SHADER, "/shaders/sel_frag.glsl");
	/** selection rendering shader */
	public static final int selP = program(selV, selF);
	public static final int SEL_STRIDE = 10;
	private static final Attribute[] selA = {
		new Attribute(glGetAttribLocation(selP, "pos"), 2, GL_SHORT, false, 0),
		new Attribute(glGetAttribLocation(selP, "size"), 2, GL_SHORT, false, 4),
		new Attribute(glGetAttribLocation(selP, "color"), 1, GL_UNSIGNED_BYTE, false, 8),
		new Attribute(glGetAttribLocation(selP, "corner"), 1, GL_UNSIGNED_BYTE, false, 9)
	};
	/** vec2: edge fading range in grid coordinates */
	public static final int sel_edgeRange = glGetUniformLocation(selP, "edgeRange");
	/** mat3x3: transformation from selection grid to screen coordinates */
	public static final int sel_transform = glGetUniformLocation(selP, "transform");

	public static final float FONT_CW = 1F/16F, FONT_CH = 1.5F/16F;

	/** default font texture for text rendering */
	public static final int font_tex = texture2DMM(GL_LINEAR_MIPMAP_NEAREST, GL_NEAREST, GL_REPEAT, GL_R3_G3_B2, "font0", "font1");
	public static final VertexArray text_vao = genTextVAO(32);
	public static final VertexArray sel_vao = genSelVAO(8);
	public static final int palette_tex = genColorPalette(
		0x00000000, 0x80000000, 0xff000000, 0xff202020, 0xff804040, 0, 0, 0, //background colors
		0xff00ff00, 0xffff0000, 0xff80ff80, 0xff8080ff, 0xffff8080, 0xffffffff, 0xffffff80, 0x80ffffff, //fg colors 1
		0xffffc0c0, 0xff4040ff, 0xffc0c0c0, 0xffc0c0ff, 0xffc0c040, 0xff40c040, 0xffc0ffc0, 0xffff4040, //fg colors 2
		0, 0, 0, 0, 0, 0, 0, 0  //fg colors 3
	);
	static {
		glUseProgram(selP);
		glUniform1i(glGetUniformLocation(selP, "palette"), 0);
		initFont(font_tex, FONT_CW, FONT_CH);
		glUniform1i(glGetUniformLocation(textP, "font"), 1);
		glUniform1i(glGetUniformLocation(textP, "palette"), 0);
	}
	private static final byte X0Y0 = 0, X1Y0 = 1, X0Y1 = 2, X1Y1 = 3;
	/**Color indices: index = BG_? | FG_? */
	public static final int
	BG_TRANSP = 0x00, BG_BLACK_T = 0x20, BG_BLACK = 0x40, BG_GRAY_D = 0x60,
	FG_TRANSP = 0, FG_BLACK_T = 1, FG_BLACK = 2, FG_GRAY_D = 3,
	FG_GREEN = 8, FG_RED = 9, FG_GREEN_L = 10, FG_BLUE_L = 11,
	FG_RED_L = 12, FG_WHITE = 13, FG_YELLOW_L = 14, FG_WHITE_T = 15,
	FG_RED_XL = 16, FG_BLUE_SL = 17, FG_GRAY_L = 18, FG_BLUE_XL = 19,
	FG_YELLOW_SL = 20, FG_GREEN_SL = 21, FG_GREEN_XL = 22, FG_RED_SL = 23,
	CURSOR_COLOR = BG_TRANSP | FG_RED_L, HIGHLIGHT_COLOR = 0x80;

	static void deleteAll() {
		glDeleteProgram(textP);
		glDeleteProgram(blockP);
		glDeleteProgram(traceP);
		glDeleteProgram(selP);
		glDeleteShader(textV);
		glDeleteShader(textF);
		glDeleteShader(blockF);
		glDeleteShader(blockV);
		glDeleteShader(traceV);
		glDeleteShader(selV);
		glDeleteShader(selF);
		glDeleteTextures(font_tex);
	}

	/**Generate a color palette texture for the given colors.
	 * @param colors should contain 32 colors in 0xAARRGGBB format (the first 8 are backgrounds).
	 * @return texture id */
	public static int genColorPalette(int... colors) {
		int tex = glGenTextures();
		glBindTexture(GL_TEXTURE_1D, tex);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA8, colors.length, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, colors);
		return tex;
	}

	/**Used to render text.
	 * @param alloc initial number of quads to allocate.
	 * @return new vertex array */
	public static VertexArray genTextVAO(int quads) {
		return new VertexArray(glGenBuffers(), GL_QUADS, TEXT_STRIDE, textA).alloc(quads * 4);
	}

	/**Used to render circuit blocks.
	 * @return new vertex array */
	public static VertexArray genBlockVAO(int quads) {
		return new VertexArray(glGenBuffers(), GL_QUADS, BLOCK_STRIDE, blockA).alloc(quads * 4);
	}

	/**Add a block to the given vertex buffer.
	 * @param buf vertex buffer
	 * @param x block x position
	 * @param y block y position
	 * @param w block total width
	 * @param h block total height
	 * @param sx horizontal stretch
	 * @param sy vertical stretch
	 * @param icon icon index */
	public static ByteBuffer drawBlock(ByteBuffer buf, int x, int y, int w, int h, AtlasSprite icon) {
		short x0 = (short)x, x1 = (short)(x + w);
		short y0 = (short)y, y1 = (short)(y + h);
		short i = (short)icon.id;
		int s = w - icon.w & 0xffff | h - icon.h << 16;
		buf.putShort(x0).putShort(y0).putInt(s).putShort(i).putShort(X0Y0);
		buf.putShort(x1).putShort(y0).putInt(s).putShort(i).putShort(X1Y0);
		buf.putShort(x1).putShort(y1).putInt(s).putShort(i).putShort(X1Y1);
		buf.putShort(x0).putShort(y1).putInt(s).putShort(i).putShort(X0Y1);
		return buf;
	}

	/**Used to render traces.
	 * @return new vertex array */
	public static VertexArray genTraceVAO(int quads) {
		return new VertexArray(glGenBuffers(), GL_QUADS, TRACE_STRIDE, traceA).alloc(quads * 4);
	}

	public static ByteBuffer drawTrace(ByteBuffer buf, int x0, int y0, int x1, int y1, int type) {
		long pos = (long)(x0 & 0xffff) | (long)(y0 & 0xffff) << 16 | (long)(x1 & 0xffff) << 32 | (long)(y1 & 0xffff) << 48;
		buf.putLong(pos).put((byte)type).put(X0Y0);
		buf.putLong(pos).put((byte)type).put(X1Y0);
		buf.putLong(pos).put((byte)type).put(X1Y1);
		buf.putLong(pos).put((byte)type).put(X0Y1);
		return buf;
	}

	/**Used to render rectangular selections.
	 * @param initial number of quads to allocate.
	 * @return new vertex array */
	public static VertexArray genSelVAO(int quads) {
		return new VertexArray(glGenBuffers(), GL_QUADS, SEL_STRIDE, selA).alloc(quads * 4);
	}

	/**Configure the text rendering shader for the given font.
	 * @param tex font texture id
	 * @param cw width of a character in texture coordinates (0.0 - 1.0).
	 * @param ch height of a character in texture coordinates (0.0 - 1.0).*/
	public static void initFont(int tex, float cw, float ch) {
		glBindTexture(GL_TEXTURE_2D, tex);
		glUseProgram(textP);
		/*if (glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER) != GL_NEAREST) {
			int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
			int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
			glUniform4f(text_tileSize, cw, ch, -0.5F / (float)w, -0.5F / (float)h);
		} else*/ glUniform4f(text_tileSize, cw, ch, 0, 0);
	}

	/**Actually render the text previously sent to {@link #print(CharSequence, int, int, int, int, int)}.
	 * @param x text grid X origin
	 * @param y text grid Y origin
	 * @param sx text grid X scale
	 * @param sy text grid Y scale */
	public static void drawText(float x, float y, float sx, float sy) {
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, font_tex);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_1D, palette_tex);
		glUseProgram(textP);
		transform(text_transform, x, y, sx, sy);
		checkGLErrors();
		text_vao.draw();
		text_vao.clear();
	}

	/**Add text to be rendered later by {@link #drawText(float, float, float, float)}.
	 * @param s the text
	 * @param c color index
	 * @param x start x grid position
	 * @param y start y grid position
	 * @param w character width
	 * @param h character height */
	public static void print(
		CharSequence s, int c,
		int x, int y, int w, int h
	) {
		try(MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(s.length() * 4 * TEXT_STRIDE);
			byte col = (byte)c;
			short x0 = (short)x, x1, y0 = (short)y, y1 = (short)(y + h);
			for (int i = 0; i < s.length(); i++, x0 = x1) {
				char ch = s.charAt(i);
				if (ch == '\n') {
					y0 = y1;
					y1 += h;
					x1 = (short)x;
					continue;
				}
				x1 = (short)(x0 + w);
				buf.putShort(x0).putShort(y0).putChar(ch).put(col).put(X0Y0);
				buf.putShort(x1).putShort(y0).putChar(ch).put(col).put(X1Y0);
				buf.putShort(x1).putShort(y1).putChar(ch).put(col).put(X1Y1);
				buf.putShort(x0).putShort(y1).putChar(ch).put(col).put(X0Y1);
			}
			if (buf.position() > 0) text_vao.append(buf.flip());
		}
	}

	/**Add a selection rectangle.
	 * @param x start x
	 * @param y start y
	 * @param w width
	 * @param h height
	 * @param c color index */
	public static void addSel(int x, int y, int w, int h, int c) {
		short x0 = (short)x, x1 = (short)(x + w),
		      y0 = (short)y, y1 = (short)(y + h);
		int size = w | h << 16;
		byte col = (byte)c;
		try(MemoryStack ms = MemoryStack.stackPush()) {
			sel_vao.append(ms.malloc(SEL_STRIDE * 4)
				.putShort(x0).putShort(y0).putInt(size).put(col).put(X0Y0)
				.putShort(x1).putShort(y0).putInt(size).put(col).put(X1Y0)
				.putShort(x1).putShort(y1).putInt(size).put(col).put(X1Y1)
				.putShort(x0).putShort(y1).putInt(size).put(col).put(X0Y1)
			.flip());
		}
	}

	/**Draw all added selection rectangles.
	 * @param x grid origin x
	 * @param y grid origin y
	 * @param sx grid scale x
	 * @param sy grid scale y
	 * @param e0 edge fade start
	 * @param e1 edge fade end */
	public static void drawSel(
		float x, float y, float sx, float sy,
		float e0, float e1
	) {
		glBindTexture(GL_TEXTURE_1D, palette_tex);
		glUseProgram(selP);
		transform(sel_transform, x, y, sx, sy);
		glUniform2f(sel_edgeRange, e0, e1);
		sel_vao.draw();
		sel_vao.clear();
	}

	/**Set the transformation for id
	 * @param id shader uniform matrix that represents a transformation
	 * @param ox offset X
	 * @param oy offset Y
	 * @param sx scale X
	 * @param sy scale Y */
	public static void transform(int id, float ox, float oy, float sx, float sy) {
		glUniformMatrix3fv(id, false, new float[] {
			sx,  0, 0,
			 0, sy, 0,
			ox, oy, 0,
		});
	}

}
