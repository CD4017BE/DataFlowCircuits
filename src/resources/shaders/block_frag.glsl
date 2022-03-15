#version 150 core
uniform sampler2D tex;
in vec4 uv;
flat in vec2 mid;
out vec4 outColor;

const vec2 ZERO2 = vec2(0, 0);

void main() {
	outColor = texture(tex, mid + min(uv.pq, ZERO2) + max(uv.st, ZERO2));
}
