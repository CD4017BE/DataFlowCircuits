#version 150 core
layout(lines) in;
layout(triangle_strip, max_vertices = 4) out;
uniform mat3x4 transform;
uniform vec2 lineSize;
in vec2[] point;
in int[] tex;
out vec2 uv;
flat out int t;
flat out float l;

void main() {
	t = tex[1];
	if (t == 0) return;
	vec2 dist = point[1] - point[0];
	float len = length(dist);
	dist = len < 0.5 ? vec2(lineSize.y, 0.0) : dist * (lineSize.y / len);
	l = len / lineSize.x - 2.0;
	vec4 icon = vec4(l + 2.0, 3.0, -2.0, -1.0);
	mat2x4 d = mat2x4(transform) * mat2(dist * 0.25, dist.y * 0.5, dist.x * -0.5);
	vec4 p0 = gl_in[0].gl_Position - d[0], p1 = gl_in[1].gl_Position + d[0];
	
	gl_Position = p1 - d[1];
	uv = icon.st;
	EmitVertex();
	gl_Position = p1 + d[1];
	uv = icon.sq;
	EmitVertex();
	gl_Position = p0 - d[1];
	uv = icon.pt;
	EmitVertex();
	gl_Position = p0 + d[1];
	uv = icon.pq;
	EmitVertex();
	EndPrimitive();
}