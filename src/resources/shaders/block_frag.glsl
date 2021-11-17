#version 150 core
uniform sampler2D tex;
in vec2 uv;
out vec4 outColor;

void main() {
	outColor = texture(tex, uv);
}
