package cd4017be.glutil;

import static org.lwjgl.opengl.GL30C.*;

/**
 * @author CD4017BE */
public class ShaderProgram {

	public final int id;

	public ShaderProgram() {
		this.id = glCreateProgram();
	}

	@Override
	protected void finalize() throws Throwable {
		glDeleteProgram(id);
	}

	public ShaderProgram attach(int shader) {
		glAttachShader(id, shader);
		return this;
	}

	public ShaderProgram detach(int shader) {
		glDetachShader(id, shader);
		return this;
	}

	public ShaderProgram link() {
		glLinkProgram(id);
		return this;
	}

	public ShaderProgram use() {
		glUseProgram(id);
		return this;
	}

	public ShaderProgram bindOutput(String name, int channel) {
		glBindFragDataLocation(id, channel, name);
		return this;
	}

	public ShaderProgram bindAttrib(String name, int idx) {
		glBindAttribLocation(id, idx, name);
		return this;
	}

	public int uniform(String name) {
		return glGetUniformLocation(id, name);
	}

	public int attrib(String name) {
		return glGetAttribLocation(id, name);
	}

	/**Setup and enable a vertex attribute.
	 * @param prog shader program id
	 * @param name attribute name
	 * @param type attribute data type
	 * @param norm whether normalizing integer values sent to float attributes
	 * @param size vector entries
	 * @param stride bytes per vertex
	 * @param ofs byte offset
	 * @return attribute index */
	public int initAttribF(
		String name, int type, boolean norm,
		int size, int stride, int ofs
	) {
		int idx = glGetAttribLocation(id, name);
		glEnableVertexAttribArray(idx);
		glVertexAttribPointer(idx, size, type, norm, stride, ofs);
		return idx;
	}

	/**Setup and enable an integer vertex attribute.
	 * @param prog shader program id
	 * @param name attribute name
	 * @param type attribute data type
	 * @param size vector entries
	 * @param stride bytes per vertex
	 * @param ofs byte offset
	 * @return attribute index */
	public int initAttribI(
		String name, int type,
		int size, int stride, int ofs
	) {
		int idx = glGetAttribLocation(id, name);
		glEnableVertexAttribArray(idx);
		glVertexAttribIPointer(idx, size, type, stride, ofs);
		return idx;
	}

	/**Set the value of a float type uniform variable.
	 * @param name variable name
	 * @param val float, vec2, vec3 or vec4 value */
	public ShaderProgram setF(String name, float... val) {
		int idx = glGetUniformLocation(id, name);
		switch(val.length) {
		case 1: glUniform1f(idx, val[0]); break;
		case 2: glUniform2f(idx, val[0], val[1]); break;
		case 3: glUniform3f(idx, val[0], val[1], val[2]); break;
		case 4: glUniform4f(idx, val[0], val[1], val[2], val[3]); break;
		default: throw new IllegalArgumentException();
		}
		return this;
	}

	/**Set the value of a integer type uniform variable.
	 * @param name variable name
	 * @param val int, ivec2, ivec3 or ivec4 value */
	public ShaderProgram setI(String name, int... val) {
		int idx = glGetUniformLocation(id, name);
		switch(val.length) {
		case 1: glUniform1i(idx, val[0]); break;
		case 2: glUniform2i(idx, val[0], val[1]); break;
		case 3: glUniform3i(idx, val[0], val[1], val[2]); break;
		case 4: glUniform4i(idx, val[0], val[1], val[2], val[3]); break;
		default: throw new IllegalArgumentException();
		}
		return this;
	}

	/**Set the value of a unsigned integer type uniform variable.
	 * @param name variable name
	 * @param val uint, uvec2, uvec3 or uvec4 value */
	public ShaderProgram setU(String name, int... val) {
		int idx = glGetUniformLocation(id, name);
		switch(val.length) {
		case 1: glUniform1ui(idx, val[0]); break;
		case 2: glUniform2ui(idx, val[0], val[1]); break;
		case 3: glUniform3ui(idx, val[0], val[1], val[2]); break;
		case 4: glUniform4ui(idx, val[0], val[1], val[2], val[3]); break;
		default: throw new IllegalArgumentException();
		}
		return this;
	}
}
