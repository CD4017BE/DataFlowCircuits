#version 150 core
uniform mat3x4 transform;
in ivec2 pos;
in int type;
out vec2 point;
out int tex;

void main() {
	gl_Position = transform * vec3(pos, 1.0);
	point = vec2(pos);
	tex = type;
}