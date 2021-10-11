#version 150 core
layout(points) in;
layout(triangle_strip, max_vertices = 4) out;
uniform mat3x4 transform;
uniform vec4 tileSize;
in vec2[] uv0;
out vec2 uv;

void main() {
	gl_Position = gl_in[0].gl_Position;
	uv = uv0[0];
	EmitVertex();
	gl_Position += transform[0];
	uv.s += tileSize.s;
	EmitVertex();
	gl_Position = gl_in[0].gl_Position + transform[1];
	uv = uv0[0] + vec2(0, tileSize.t);
	EmitVertex();
	gl_Position += transform[0];
	uv.s += tileSize.s;
	EmitVertex();
	EndPrimitive();
}