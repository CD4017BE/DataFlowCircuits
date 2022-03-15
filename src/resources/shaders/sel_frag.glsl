#version 150 core
uniform vec2 edgeRange;
uniform vec4 bgColor;
in vec4 fcolor;
in vec4 edges;
out vec4 outColor;

void main() {
	vec2 v = min(edges.xy, edges.zw);
	outColor = mix(fcolor, bgColor, smoothstep(edgeRange.x, edgeRange.y, min(v.x, v.y)));
}