#version 150 core
uniform mat3x4 transform;
uniform vec2 gridScale;
in uvec4 pos;
in uint size;
out vec4 dx, dy;
out vec4 icon;

void main() {
	gl_Position = transform * vec3(pos.xy, 1.0);
	vec2 d = vec2(size & 15u, size >> 4 & 15u);
	dx = transform[0] * d.x;
	dy = transform[1] * d.y;
	icon.st = vec2(pos.zw) * gridScale;
	icon.pq = icon.st + d * gridScale;
}