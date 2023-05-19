#version 110

uniform vec4 bg_color, fg_color;
uniform float linewidth;
varying vec2 pos;

void main() {
	gl_FragColor = any(lessThan(fract(pos), vec2(linewidth))) ? fg_color : bg_color;
}
