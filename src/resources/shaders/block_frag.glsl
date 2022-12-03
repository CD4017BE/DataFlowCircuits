#version 110
uniform sampler2D texture;
varying vec2 tex0, tex1, size, ofs;

void main() {
	gl_FragColor = texture2DLod(texture,
		mix(
			mix(tex0, mod(tex0, size), step(0.0, tex0)),
			tex1 + size, step(0.0, tex1)
		) + ofs, 0.0
	);
}
