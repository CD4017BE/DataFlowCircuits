#version 150 core
layout(points) in;
layout(triangle_strip, max_vertices = 4) out;
in vec4[] dx, dy;
in vec4[] icon;
out vec2 uv;

void main() {
	gl_Position = gl_in[0].gl_Position;
	uv = icon[0].st;
	EmitVertex();
	gl_Position += dx[0];
	uv = icon[0].pt;
	EmitVertex();
	gl_Position = gl_in[0].gl_Position + dy[0];
	uv = icon[0].sq;
	EmitVertex();
	gl_Position += dx[0];
	uv = icon[0].pq;
	EmitVertex();
	EndPrimitive();
}