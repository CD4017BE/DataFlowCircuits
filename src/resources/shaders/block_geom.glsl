#version 150 core
layout(points) in;
layout(triangle_strip, max_vertices = 4) out;
in vec4[] dx, dy;
in vec4[] icon;
in float[] du;
out vec4 uv; //(u0, u1, v, sel)
flat out float mid;

void main() {
	gl_Position = gl_in[0].gl_Position;
	float u0 = icon[0].s - du[0];
	float u1 = icon[0].p + du[0];
	mid = (u0 + u1) * 0.5;
	uv.yzxw = vec4(icon[0].st, u0, 1.0);
	EmitVertex();
	gl_Position += dx[0];
	uv.xzyw = vec4(icon[0].pt, u1, 0.0);
	EmitVertex();
	gl_Position = gl_in[0].gl_Position + dy[0];
	uv.yzxw = vec4(icon[0].sq, u0, 1.0);
	EmitVertex();
	gl_Position += dx[0];
	uv.xzyw	= vec4(icon[0].pq, u1, 0.0);
	EmitVertex();
	EndPrimitive();
}