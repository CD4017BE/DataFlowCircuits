#version 110
uniform sampler2D tex;
uniform vec2 texScale;
varying vec2 uv;
varying float t, l;

void main() {
	float ofs;
	if (uv.y >= 0.0 && uv.y < 1.0) ofs = t;
	else ofs = (uv.x < 0.0 || uv.x >= l ? 2.0 : 0.0) - floor(uv.y);
	gl_FragColor = texture2D(tex, (uv + vec2(0.0, ofs)) * texScale);
}
