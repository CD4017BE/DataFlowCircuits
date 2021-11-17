#version 150 core
uniform sampler2D tex;
in vec2 uv;
flat in int t;
out vec4 outColor;

void main() {
	ivec2 pos = ivec2(floor(uv));
	bool odd = (pos.y & 1) != 0;
	pos.y = t >> 4 * pos.y;
	outColor = texelFetch(tex, pos & 15, 0);
	if (odd) outColor.rgb *= 0.75;
}
