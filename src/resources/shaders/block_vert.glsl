#version 150 core
uniform mat3x4 transform;
uniform vec2 gridScale;
uniform isamplerBuffer atlas; //(u0, v0, u1, v1)
in ivec4 data; //(posX, posY, extWH, id)
out vec4 icon, dx, dy;
out vec2 duv;

void main() {
	gl_Position = transform * vec3(data.xy, 1.0);
	ivec4 grid = texelFetch(atlas, data.w) + ivec4(0, 0, 1, 1);
	icon = vec4(grid) * gridScale.xyxy;
	int extW = data.z & 0xff, extH = data.z >> 8;
	dx = transform[0] * float(grid.p - grid.s + extW);
	dy = transform[1] * float(grid.q - grid.t + extH);
	duv = vec2(extW, extH) * gridScale;
}