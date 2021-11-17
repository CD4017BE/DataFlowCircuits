#version 150 core
uniform mat3x4 transform;
in uvec4 pos;
in vec4 colorIn;
out vec2 size;
out vec4 color;

void main() {
	gl_Position = transform * vec3(pos.xy, 1.0);
	size = vec2(pos.zw);
	color = colorIn;
}