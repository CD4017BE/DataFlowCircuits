package cd4017be.dfc.editor;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static cd4017be.util.GLUtils.*;
import static org.lwjgl.opengl.GL11C.GL_POINTS;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL21C.glUniformMatrix3x4fv;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL32C.*;

import org.lwjgl.system.MemoryStack;

/**Loads and configures all OpenGL shaders used by the program.
 * @author CD4017BE */
public class Shaders {

	private static final int textV = loadShader(GL_VERTEX_SHADER, "/shaders/text_vert.glsl");
	private static final int textG = loadShader(GL_GEOMETRY_SHADER, "/shaders/text_geom.glsl");
	private static final int textF = loadShader(GL_FRAGMENT_SHADER, "/shaders/text_frag.glsl");
	/** text rendering shader */
	public static final int textP = program(textV, textG, textF, "outColor");
	private static final int text_charCode = glGetAttribLocation(textP, "charCode");
	private static final int text_tileSize = glGetUniformLocation(textP, "tileSize");
	private static final int text_tileStride = glGetUniformLocation(textP, "tileStride");
	/** mat3x4: transformation from character grid to screen coordinates */
	public static final int text_transform = glGetUniformLocation(textP, "transform");
	/** uint: number of characters per line */
	public static final int text_wrap = glGetUniformLocation(textP, "lineWrap");
	/** vec4: text foreground color RGBA */
	public static final int text_fgColor = glGetUniformLocation(textP, "fgColor");
	/** vec4: text background color RGBA */
	public static final int text_bgColor = glGetUniformLocation(textP, "bgColor");

	private static final int blockV = loadShader(GL_VERTEX_SHADER, "/shaders/block_vert.glsl");
	private static final int blockG = loadShader(GL_GEOMETRY_SHADER, "/shaders/block_geom.glsl");
	private static final int blockF = loadShader(GL_FRAGMENT_SHADER, "/shaders/block_frag.glsl");
	/** block rendering shader */
	public static final int blockP = program(blockV, blockG, blockF, "outColor");
	private static final int block_data = glGetAttribLocation(blockP, "data");
	/** vec2: texture coordinate to block grid scale */
	public static final int block_gridScale = glGetUniformLocation(blockP, "gridScale");
	/** mat3x4: transformation from block grid to screen coordinates */
	public static final int block_transform = glGetUniformLocation(blockP, "transform");

	private static final int traceV = loadShader(GL_VERTEX_SHADER, "/shaders/wire_vert.glsl");
	private static final int traceG = loadShader(GL_GEOMETRY_SHADER, "/shaders/wire_geom.glsl");
	private static final int traceF = loadShader(GL_FRAGMENT_SHADER, "/shaders/wire_frag.glsl");
	/** trace rendering shader */
	public static final int traceP = program(traceV, traceG, traceF, "outColor");
	private static final int trace_pos = glGetAttribLocation(traceP, "pos");
	private static final int trace_types = glGetAttribLocation(traceP, "types");
	/** float: trace line thickness in grid coordinates */
	public static final int trace_lineSize = glGetUniformLocation(traceP, "lineSize");
	/** mat3x4: transformation from trace grid to screen coordinates */
	public static final int trace_transform = glGetUniformLocation(traceP, "transform");

	private static final int selV = loadShader(GL_VERTEX_SHADER, "/shaders/sel_vert.glsl");
	private static final int selG = loadShader(GL_GEOMETRY_SHADER, "/shaders/sel_geom.glsl");
	private static final int selF = loadShader(GL_FRAGMENT_SHADER, "/shaders/sel_frag.glsl");
	/** selection rendering shader */
	public static final int selP = program(selV, selG, selF, "outColor");
	private static final int sel_pos = glGetAttribLocation(selP, "pos");
	private static final int sel_colorIn = glGetAttribLocation(selP, "colorIn");
	/** vec4: selection background color RGBA */
	public static final int sel_bgColor = glGetUniformLocation(selP, "bgColor");
	/** vec2: edge fading range in grid coordinates */
	public static final int sel_edgeRange = glGetUniformLocation(selP, "edgeRange");
	/** mat3x4: transformation from selection grid to screen coordinates */
	public static final int sel_transform = glGetUniformLocation(selP, "transform");

	/**Vertex format sizes */
	public static final int FONT_TEX_STRIDE = 16,
	SEL_STRIDE = 12, TRACE_STRIDE = 6, BLOCK_STRIDE = 8;
	public static final float FONT_CW = 1F/16F, FONT_CH = 1.5F/16F;

	/** default font texture for text rendering */
	public static final int font_tex = texture2D(GL_LINEAR, GL_REPEAT, GL_R8, "font");
	public static final int char_buf = glGenBuffers();
	public static final int text_vao = genTextVAO(char_buf);
	public static final int sel_buf = glGenBuffers();
	public static final int sel_vao = genSelVAO(sel_buf);
	static {initFont(font_tex, FONT_CW, FONT_CH, FONT_TEX_STRIDE);}

	static void deleteAll() {
		glDeleteProgram(textP);
		glDeleteProgram(blockP);
		glDeleteProgram(traceP);
		glDeleteProgram(selP);
		glDeleteShader(textV);
		glDeleteShader(textG);
		glDeleteShader(textF);
		glDeleteShader(blockF);
		glDeleteShader(blockV);
		glDeleteShader(blockG);
		glDeleteShader(traceV);
		glDeleteShader(traceG);
		glDeleteShader(selV);
		glDeleteShader(selG);
		glDeleteShader(selF);
		glDeleteTextures(font_tex);
	}

	/**@param charBuf buffer id for storing ASCII data.
	 * @return new vertex array id */
	public static int genTextVAO(int charBuf) {
		int vao = glGenVertexArrays();
		glBindVertexArray(vao);
		glEnableVertexAttribArray(text_charCode);
		glBindBuffer(GL_ARRAY_BUFFER, charBuf);
		glVertexAttribIPointer(text_charCode, 1, GL_UNSIGNED_BYTE, 0, 0);
		glBindVertexArray(0);
		checkGLErrors();
		return vao;
	}

	/**Used to render circuit blocks.
	 * @param buf vertex buffer id
	 * @return new vertex array id */
	public static int genBlockVAO(int buf) {
		int vao = glGenVertexArrays();
		glBindVertexArray(vao);
		glEnableVertexAttribArray(block_data);
		glBindBuffer(GL_ARRAY_BUFFER, buf);
		glVertexAttribIPointer(block_data, 4, GL_SHORT, BLOCK_STRIDE, 0);
		glBindVertexArray(0);
		checkGLErrors();
		return vao;
	}

	/**Used to render traces.
	 * @param buf vertex buffer id
	 * @return new vertex array id */
	public static int genTraceVAO(int buf) {
		int vao = glGenVertexArrays();
		glBindVertexArray(vao);
		glEnableVertexAttribArray(trace_pos);
		glEnableVertexAttribArray(trace_types);
		glBindBuffer(GL_ARRAY_BUFFER, buf);
		glVertexAttribIPointer(trace_pos, 2, GL_SHORT, TRACE_STRIDE, 0);
		glVertexAttribIPointer(trace_types, 1, GL_UNSIGNED_SHORT, TRACE_STRIDE, 4);
		glBindVertexArray(0);
		checkGLErrors();
		return vao;
	}

	/**Used to render rectangular selections.
	 * @param buf vertex buffer id
	 * @return new vertex array id */
	public static int genSelVAO(int buf) {
		int vao = glGenVertexArrays();
		glBindVertexArray(vao);
		glEnableVertexAttribArray(sel_pos);
		glEnableVertexAttribArray(sel_colorIn);
		glBindBuffer(GL_ARRAY_BUFFER, buf);
		glVertexAttribIPointer(sel_pos, 4, GL_SHORT, SEL_STRIDE, 0);
		glVertexAttribPointer(sel_colorIn, GL_BGRA, GL_UNSIGNED_BYTE, true, SEL_STRIDE, 8);
		glBindVertexArray(0);
		checkGLErrors();
		return vao;
	}

	/**Configure the text rendering shader for the given font.
	 * @param tex font texture id
	 * @param cw width of a character in texture coordinates (0.0 - 1.0).
	 * @param ch height of a character in texture coordinates (0.0 - 1.0).
	 * @param stride number of characters arranged in each row */
	public static void initFont(int tex, float cw, float ch, int stride) {
		glBindTexture(GL_TEXTURE_2D, tex);
		glUseProgram(textP);
		if (glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER) != GL_NEAREST) {
			int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
			int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
			glUniform4f(text_tileSize, cw, ch, -0.5F / (float)w, -0.5F / (float)h);
		} else glUniform4f(text_tileSize, cw, ch, 0, 0);
		glUniform1ui(text_tileStride, stride);
	}

	public static void startText() {
		glBindBuffer(GL_ARRAY_BUFFER, char_buf);
		glBindTexture(GL_TEXTURE_2D, font_tex);
		glBindVertexArray(text_vao);
		glUseProgram(textP);
	}

	public static void print(
		CharSequence s, int wrap, int fg, int bg,
		float x, float y, float sx, float sy
	) {
		int l = s.length(), bl = glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
		if (l > bl) glBufferData(GL_ARRAY_BUFFER, Math.max(l, bl<<1), GL_STREAM_DRAW);
		try (MemoryStack ms = MemoryStack.stackPush()) {
			glBufferSubData(GL_ARRAY_BUFFER, 0, ms.ASCII(s, false));
		}
		checkGLErrors();
		glUniform1ui(text_wrap, wrap);
		setColor(text_bgColor, bg);
		setColor(text_fgColor, fg);
		glUniformMatrix3x4fv(text_transform, false, new float[] {
			sx,  0, 0, 0,
			 0, sy, 0, 0,
			 x,  y, 0, 1
		});
		glDrawArrays(GL_POINTS, 0, l);
		checkGLErrors();
	}

	public static void allocSelBuf(int n) {
		glBindBuffer(GL_ARRAY_BUFFER, sel_buf);
		int l = glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
		if ((n *= SEL_STRIDE) > l)
			glBufferData(GL_ARRAY_BUFFER, n, GL_STREAM_DRAW);
	}

	static int sel_count;

	public static void addSel(int x, int y, int w, int h, int c) {
		glBindBuffer(GL_ARRAY_BUFFER, sel_buf);
		try(MemoryStack ms = MemoryStack.stackPush()) {
			glBufferSubData(GL_ARRAY_BUFFER, sel_count++ * SEL_STRIDE,
				ms.malloc(SEL_STRIDE).putShort((short)x).putShort((short)y)
				.putShort((short)w).putShort((short)h).putInt(c).flip()
			);
		}
	}

	public static void drawSel(
		float x, float y, float sx, float sy,
		float e0, float e1, int bg
	) {
		glUseProgram(selP);
		glUniformMatrix3x4fv(sel_transform, false, new float[] {
			sx,  0, 0, 0,
			 0, sy, 0, 0,
			 x,  y, 0, 1
		});
		glUniform2f(sel_edgeRange, e0, e1);
		setColor(sel_bgColor, bg);
		glBindVertexArray(sel_vao);
		glDrawArrays(GL_POINTS, 0, sel_count);
		sel_count = 0;
		checkGLErrors();
	}

}
