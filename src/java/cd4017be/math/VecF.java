package cd4017be.math;


/**Holds a fixed sized array of 4D float vectors to perform linear algebra with.
 * Vector arguments (vec4) to functions are specified by their index in the vector array.
 * Matrix arguments (matNx4) are specified by the index of their first row vector with
 * the remaining rows stored in subsequent indices.
 * @author CD4017BE */
public final class VecF {

	//public static final ThreadLocal<VecF> XMM = ThreadLocal.withInitial(()-> new VecF(16));

	/** vector array */
	public final float[] va;

	/**@param size number of vec4 entries */
	public VecF(int size) {
		this.va = new float[size*4];
	}

	/**@param i = vec4
	 * @param j element index
	 * @return i[j] */
	public float get(int i, int j) {
		return va[i*4 + j];
	}

	/**sets i[j] = x
	 * @param i = vec4
	 * @param j element index
	 * @param x = scalar
	 * @return this */
	public VecF set(int i, int j, float x) {
		va[i*4 + j] = x;
		return this;
	}

	/**Vector load
	 * @param i = vec4(vec)
	 * @param vec = vec4
	 * @return this */
	public VecF load(int i, float... vec) {
		i *= 4;
		va[i+0] = vec[0];
		va[i+1] = vec[1];
		va[i+2] = vec[2];
		va[i+3] = vec[3];
		return this;
	}

	/**Matrix load
	 * @param n number of rows
	 * @param i = matNx4(vec)
	 * @param vec = matNx4 in row major order
	 * @return this */
	public VecF mload(int n, int i, float... vec) {
		n *= 4; i *= 4;
		for (int j = 0; j < n; j++)
			va[i+j] = vec[j];
		return this;
	}

	/**Vector store
	 * @param i = vec4
	 * @param vec = vec4(i)
	 * @return vec */
	public float[] store(int i, float[] vec) {
		return mstore(1, i, vec);
	}

	/**Matrix store
	 * @param n number of rows
	 * @param i = matNx4
	 * @param vec = matNx4(i) in row major order
	 * @return vec */
	public float[] mstore(int n, int i, float[] vec) {
		n *= 4; i *= 4;
		for (int j = 0; j < n; j++)
			vec[j] = va[i+j];
		return vec;
	}

	/**Vector addition
	 * @param r = vec4(a + b)
	 * @param a = vec4
	 * @param b = vec4
	 * @return this */
	public VecF add(int r, int a, int b) {
		float[] va = this.va;
		r *= 4; a *= 4; b *= 4;
		va[r+0] = va[a+0] + va[b+0];
		va[r+1] = va[a+1] + va[b+1];
		va[r+2] = va[a+2] + va[b+2];
		va[r+3] = va[a+3] + va[b+3];
		return this;
	}

	/**Matrix addition
	 * @param n number of rows (1..4)
	 * @param r = matNx4(a + b)
	 * @param a = matNx4
	 * @param b = matNx4
	 * @return this */
	public VecF madd(int n, int r, int a, int b) {
		switch(n) {
		case 4: add(r+3, a+3, b+3);
		case 3: add(r+2, a+2, b+2);
		case 2: add(r+1, a+1, b+1);
		default: return add(r, a, b);
		}
	}

	/**Vector subtraction
	 * @param r = vec4(a - b)
	 * @param a = vec4
	 * @param b = vec4
	 * @return this */
	public VecF sub(int r, int a, int b) {
		float[] va = this.va;
		r *= 4; a *= 4; b *= 4;
		va[r+0] = va[a+0] - va[b+0];
		va[r+1] = va[a+1] - va[b+1];
		va[r+2] = va[a+2] - va[b+2];
		va[r+3] = va[a+3] - va[b+3];
		return this;
	}

	/**Matrix subtraction
	 * @param n number of rows
	 * @param r = matNx4(a - b)
	 * @param a = matNx4
	 * @param b = matNx4
	 * @return this */
	public VecF msub(int n, int r, int a, int b) {
		switch(n) {
		case 4: sub(r+3, a+3, b+3);
		case 3: sub(r+2, a+2, b+2);
		case 2: sub(r+1, a+1, b+1);
		default: return sub(r, a, b);
		}
	}

	/**Vector * Scalar multiplication
	 * @param r = vec4(a * s)
	 * @param a = vec4
	 * @param s = scalar
	 * @return this */
	public VecF sca(int r, int a, float s) {
		float[] va = this.va;
		r *= 4; a *= 4;
		va[r+0] = va[a+0] * s;
		va[r+1] = va[a+1] * s;
		va[r+2] = va[a+2] * s;
		va[r+3] = va[a+3] * s;
		return this;
	}

	/**Matrix * Scalar multiplication
	 * @param n number of rows
	 * @param r = matNx4(a * s)
	 * @param a = matNx4
	 * @param s = scalar
	 * @return this */
	public VecF msca(int n, int r, int a, float s) {
		switch(n) {
		case 4: sca(r+3, a+3, s);
		case 3: sca(r+2, a+2, s);
		case 2: sca(r+1, a+1, s);
		default: return sca(r, a, s);
		}
	}

	/**Vector fused multiply add
	 * @param r = vec4(a + b * s)
	 * @param a = vec4
	 * @param b = vec4
	 * @param s = scalar
	 * @return this */
	public VecF fma(int r, int a, int b, float s) {
		float[] va = this.va;
		r *= 4; a *= 4; b *= 4;
		va[r+0] = Math.fma(va[b+0], s, va[a+0]);
		va[r+1] = Math.fma(va[b+1], s, va[a+1]);
		va[r+2] = Math.fma(va[b+2], s, va[a+2]);
		va[r+3] = Math.fma(va[b+3], s, va[a+3]);
		return this;
	}

	/**negate Vector
	 * @param r = vec4(-a)
	 * @param a = vec4
	 * @return this */
	public VecF neg(int r, int a) {
		float[] va = this.va;
		r *= 4; a *= 4;
		va[r+0] = -va[a+0];
		va[r+1] = -va[a+1];
		va[r+2] = -va[a+2];
		va[r+3] = -va[a+3];
		return this;
	}

	/**Vector * Vector inner product
	 * @param a = vec4
	 * @param b = vec4
	 * @return a * b */
	public float dot(int a, int b) {
		float[] va = this.va;
		a *= 4; b *= 4;
		return (va[a+0] * va[b+0] + va[a+1] * va[b+1])
		     + (va[a+2] * va[b+2] + va[a+3] * va[b+3]);
	}

	/**Vector * Vector outer product.
	 * Note: r can't share storage with a or b!
	 * @param r = mat4x4(a * b)
	 * @param a = vec4
	 * @param b = vec4
	 * @return this */
	public VecF mul11(int r, int a, int b) {
		b *= 4;
		return sca(r, a, va[b])
		.sca(r+1, a, va[b+1])
		.sca(r+2, a, va[b+2])
		.sca(r+3, a, va[b+3]);
	}

	/**Matrix * Vector multiplication
	 * @param r = vec4(a * b)
	 * @param a = mat4x4
	 * @param b = vec4
	 * @return this */
	public VecF mul41(int r, int a, int b) {
		r *= 4;
		return load(r,
			dot(a+0, b),
			dot(a+1, b),
			dot(a+2, b),
			dot(a+3, b)
		);
	}

	/**Vector * Matrix multiplication.
	 * Note: r can't share storage with b!
	 * @param r = vec4(a * b)
	 * @param a = vec4
	 * @param b = mat4x4
	 * @return this */
	public VecF mul14(int r, int a, int b) {
		a *= 4;
		float a0 = va[a], a1 = va[a+1], a2 = va[a+2], a3 = va[a+3];
		return sca(r, b, a0)
		.fma(r, r, b+1, a1)
		.fma(r, r, b+2, a2)
		.fma(r, r, b+3, a3);
	}

	/**Matrix * Matrix multiplication.
	 * Note: r can't share storage with b!
	 * @param r = mat4x4(a * b)
	 * @param a = mat4x4
	 * @param b = mat4x4
	 * @return this */
	public VecF mul44(int r, int a, int b) {
		for (int i = 0; i < 4; i++, a++, r++)
			mul14(r, a, b);
		return this;
	}

	/**Normalize Vector
	 * @param r = vec4(a / |a|)
	 * @param a = vec4
	 * @return this */
	public VecF norm(int r, int a) {
		return sca(r, a, (float)(1.0/Math.sqrt(dot(a, a))));
	}

	/**3D-Vector cross product
	 * @param r = vec4(a * b, 0)
	 * @param a = vec3
	 * @param b = vec3
	 * @return this */
	public VecF cross(int r, int a, int b) {
		float[] va = this.va;
		a *= 4; b *= 4;
		return dir3(r,
			va[a+1] * va[b+2] - va[a+2] * va[b+1],
			va[a+2] * va[b+0] - va[a+0] * va[b+2],
			va[a+0] * va[b+1] - va[a+1] * va[b+0]
		);
	}

	/**Set 3D position vector
	 * @param i = vec4(vec, 1)
	 * @param vec = vec3
	 * @return this */
	public VecF pos3(int i, float... vec) {
		float[] va = this.va;
		i *= 4;
		va[i+0] = vec[0];
		va[i+1] = vec[1];
		va[i+2] = vec[2];
		va[i+3] = 1F;
		return this;
	}

	/**Set 3D direction vector
	 * @param i = vec4(vec, 1)
	 * @param vec = vec3
	 * @return this */
	public VecF dir3(int i, float... vec) {
		i *= 4;
		va[i+0] = vec[0];
		va[i+1] = vec[1];
		va[i+2] = vec[2];
		va[i+3] = 0F;
		return this;
	}

	/**Store 3D vector
	 * @param i = vec3
	 * @param vec = vec3(i)
	 * @return this */
	public VecF store3(int i, float[] vec) {
		i *= 4;
		vec[0] = va[i+0];
		vec[1] = va[i+1];
		vec[2] = va[i+2];
		return this;
	}

	public VecF diag4(int r, int v) {
		float[] va = this.va;
		r *= 4; v *= 4;
		va[r+ 0] = va[v+0];
		va[r+ 5] = va[v+1];
		va[r+10] = va[v+2];
		va[r+15] = va[v+3];
		va[r+ 1] = va[r+ 2] = va[r+ 3] = va[r+ 4] = 0;
		va[r+ 6] = va[r+ 7] = va[r+ 8] = va[r+ 9] = 0;
		va[r+11] = va[r+12] = va[r+13] = va[r+14] = 0;
		return this;
	}

}
