#version 150 core
uniform vec2 edgeRange;
in vec4 fcolor;
in vec4 edges;
out vec4 outColor;

void main() {
	outColor = fcolor;
	vec2 v = min(edges.xy, edges.zw);
	outColor.a *= 1.0 - smoothstep(edgeRange.x, edgeRange.y, min(v.x, v.y));
}