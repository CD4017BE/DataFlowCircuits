package cd4017be.util;

import static cd4017be.dfc.editor.Main.checkGLErrors;
import static java.lang.Math.max;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_BUFFER_SIZE;
import static org.lwjgl.opengl.GL15C.glGetBufferParameteri;
import static org.lwjgl.opengl.GL20C.*;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

/**
 * @author cd4017be */
public class VertexArray {

	public static record Attribute(int id, int size, int type, boolean norm, int offset) {
		public Attribute(int prog, String name, int size, int type, boolean norm, int offset) {
			this(glGetAttribLocation(prog, name), size, type, norm, offset);
		}
	}

	public final int buffer, mode, stride;
	final Attribute[] attributes;
	public int count;

	public VertexArray(int buffer, int mode, int stride, Attribute... attributes) {
		this.buffer = buffer;
		this.mode = mode;
		this.stride = stride;
		this.attributes = attributes;
	}

	public VertexArray alloc(int vertices) {
		bind();
		enlarge(vertices * stride);
		return this;
	}

	public void clear() {
		count = 0;
	}

	public void bind() {
		glBindBuffer(GL_ARRAY_BUFFER, buffer);
	}

	public void draw() {
		bind();
		for (Attribute attr : attributes) {
			glEnableVertexAttribArray(attr.id);
			glVertexAttribPointer(attr.id, attr.size, attr.type, attr.norm, stride, attr.offset);
		}
		glDrawArrays(mode, 0, count);
		for (Attribute attr : attributes)
			glDisableVertexAttribArray(attr.id);
		checkGLErrors(1);
	}

	public void set(int ofs, ByteBuffer data) {
		bind();
		ofs *= stride;
		int len = data.remaining();
		int cap = glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
		if (len + ofs > cap) enlarge(max(len + ofs, cap << 1));
		glBufferSubData(GL_ARRAY_BUFFER, ofs, data);
		count = max(count, (ofs + len) / stride);
	}

	public void append(ByteBuffer data) {
		set(count, data);
	}

	private void enlarge(int size) {
		if (count == 0)
			glBufferData(GL_ARRAY_BUFFER, size, GL_DYNAMIC_DRAW);
		else try (MemoryStack ms = MemoryStack.stackPush()) {
			ByteBuffer buf = ms.malloc(count * stride);
			glGetBufferSubData(GL_ARRAY_BUFFER, 0, buf);
			glBufferData(GL_ARRAY_BUFFER, size, GL_DYNAMIC_DRAW);
			glBufferSubData(GL_ARRAY_BUFFER, 0, buf);
		}
	}

}
