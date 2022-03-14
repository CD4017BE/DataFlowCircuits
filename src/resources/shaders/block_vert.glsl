#version 150 core
uniform mat3x4 transform;
uniform vec2 gridScale;
uniform isamplerBuffer atlas; // vec4(u0, v0, u1, v1)
in ivec3 pos;
in int id;
out vec4 icon, dx, dy;
out float du;

void main() {
	gl_Position = transform * vec3(pos.xy, 1.0);
	ivec4 grid = texelFetch(atlas, id) + ivec4(0, 0, 1, 1);
	dx = transform[0] * float(grid.p - grid.s + pos.z);
	dy = transform[1] * float(grid.q - grid.t);
	icon = vec4(grid) * gridScale.xyxy;
	du = float(pos.z) * gridScale.x;
}