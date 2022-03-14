#version 150 core
uniform sampler2D tex;
in vec4 uv; //(u0, u1, v, sel)
flat in float mid;
out vec4 outColor;

void main() {
	outColor = texture(tex,
		vec2(uv.w < 0.5 ? max(uv.x, mid) : min(uv.y, mid), uv.z)
	);
}
