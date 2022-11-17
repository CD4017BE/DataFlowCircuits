#version 110
uniform mat3 transform;
uniform sampler1D palette;

attribute vec2 pos, size;
attribute float color, corner;
varying vec4 fgColor, bgColor, edges;

void main() {
	gl_Position = vec4(transform * vec3(pos, 1.0), 1.0);
	float bgi = floor(color / 32.0), fgi = floor(color - bgi * 32.0);
	fgColor = texture1D(palette, fgi / 32.0);
	bgColor = texture1D(palette, bgi / 32.0);
	vec2 c = vec2(mod(corner, 2.0), floor(corner / 2.0));
	edges.xy = (1.0 - c) * size;
	edges.zw = c * size;
}