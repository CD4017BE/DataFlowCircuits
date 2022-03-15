#version 150 core
layout(points) in;
layout(triangle_strip, max_vertices = 4) out;
in vec4[] icon, dx, dy;
in vec2[] duv;
out vec4 uv; //(u0, v0, u1, v1)
out vec2 sel;
flat out vec2 mid;

void main() {
	mid = (icon[0].st + icon[0].pq) * 0.5;
	vec4 iuv = icon[0] - mid.stst;
	vec4 euv = iuv + vec4(-duv[0], duv[0]);
	uv.st = euv.st;
	uv.pq = iuv.st;
	gl_Position = gl_in[0].gl_Position;
	EmitVertex();
	uv.pt = euv.pt;
	uv.sq = iuv.pt;
	gl_Position += dx[0];
	EmitVertex();
	uv.sq = euv.sq;
	uv.pt = iuv.sq;
	gl_Position = gl_in[0].gl_Position + dy[0];
	EmitVertex();
	uv.pq = euv.pq;
	uv.st = iuv.pq;
	gl_Position += dx[0];
	EmitVertex();
	EndPrimitive();
}