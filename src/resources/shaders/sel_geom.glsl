#version 150 core
layout(points) in;
layout(triangle_strip, max_vertices = 4) out;
uniform mat3x4 transform;
in vec2[] size;
in vec4[] color;
out vec4 fcolor;
out vec4 edges;

void main() {
	fcolor = color[0];
	vec4 dx = transform[0] * size[0].x;
	vec4 dy = transform[1] * size[0].y;
	gl_Position = gl_in[0].gl_Position;
	edges = vec4(size[0], 0, 0);
	EmitVertex();
	gl_Position += dx;
	edges = edges.zyxw;
	EmitVertex();
	gl_Position = gl_in[0].gl_Position + dy;
	edges = edges.zwxy;
	EmitVertex();
	gl_Position += dx;
	edges = edges.zyxw;
	EmitVertex();
	EndPrimitive();
}