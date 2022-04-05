#version 150 core
uniform sampler2D tex;
in vec2 uv;
flat in int t;
flat in float l;
out vec4 outColor;

void main() {
	ivec2 pos = ivec2(floor(uv));
	pos.x &= 7;
	if (pos.y >= 0 && pos.y < 2)
		pos += ivec2((t & 48) >> 1, (t & 15) << 1);
	else {
		pos.y &= 1;
		if (uv.x < 0 || uv.x >= l) pos.x += 16;
	}
	outColor = texelFetch(tex, pos, 0);
}
