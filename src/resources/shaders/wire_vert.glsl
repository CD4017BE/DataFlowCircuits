#version 110
uniform mat3 transform;
uniform vec2 lineSize;

attribute vec4 pos;
attribute float type, corner;
varying vec2 uv;
varying float t, l;

void main() {
	t = type + 2.0;
	vec2 dist = pos.zw - pos.xy;
	float len = length(dist);
	dist = len < 0.5 ? vec2(lineSize.y, 0.0) : dist * (lineSize.y / len);
	vec2 c = vec2(mod(corner, 2.0), floor(corner / 2.0));
	gl_Position = vec4(transform * vec3(mix(pos.xy, pos.zw, c.x) + mat2(dist * 0.5, dist.y, -dist.x) * (c - 0.5), 1.0), 1.0);
	l = (len / lineSize.y - 0.5) * lineSize.x;
	float d = 0.5 * lineSize.x;
	uv = mix(vec2(l + d, -0.5), vec2(-d, 1.5), c);
}