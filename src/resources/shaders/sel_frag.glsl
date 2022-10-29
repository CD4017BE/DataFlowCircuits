#version 110
uniform vec2 edgeRange;

varying vec4 fgColor, bgColor, edges;

void main() {
	vec2 v = min(edges.xy, edges.zw);
	gl_FragColor = mix(fgColor, bgColor, smoothstep(edgeRange.x, edgeRange.y, min(v.x, v.y)));
}