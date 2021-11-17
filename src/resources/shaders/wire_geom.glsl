#version 150 core
layout(lines) in;
layout(triangle_strip, max_vertices = 4) out;
uniform mat3x4 transform;
uniform vec2 lineSize;
in vec2[] point;
in int[] type;
out vec2 uv;
flat out int t;

void main() {
	t = type[1];
	if (t == 0) return;
	vec2 dist = point[1] - point[0];
	float len = length(dist);
	dist = len < 0.5 ? vec2(lineSize.y, 0.0) : dist * (lineSize.y / len);
	
	vec4 icon = vec4(0.0, 4.0, len / lineSize.x + 2.0, 0.0);
	mat2x4 d = mat2x4(transform) * mat2(dist * 0.25, dist.y, -dist.x);
	vec4 p0 = gl_in[0].gl_Position - d[0], p1 = gl_in[1].gl_Position + d[0];
	
	if ((t & 0xff00) == 0) {
		if ((t & 0xf0) == 0) icon.yw *= 0.5;
		len = 0.25;
	} else len = (t & 0xf000) != 0 ? 0.5 : 0.375;
	vec4 d1 = d[1] * len, d0 = d1 - d[1];
	
	gl_Position = p1 + d0;
	uv = icon.st;
	EmitVertex();
	gl_Position = p1 + d1;
	uv = icon.sq;
	EmitVertex();
	gl_Position = p0 + d0;
	uv = icon.pt;
	EmitVertex();
	gl_Position = p0 + d1;
	uv = icon.pq;
	EmitVertex();
	EndPrimitive();
}