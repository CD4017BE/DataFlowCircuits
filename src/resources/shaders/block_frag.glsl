#version 110
uniform sampler2D texture;
varying vec2 tex0, tex1, f;

void main() {
	gl_FragColor = texture2D(texture, mix(tex0, tex1, clamp(f, 0.0, 1.0)));
}
