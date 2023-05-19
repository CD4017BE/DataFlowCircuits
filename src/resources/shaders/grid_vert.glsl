#version 110

attribute vec2 vertex, coords;
varying vec2 pos;

void main() {
	gl_Position = vec4(vertex, 0.0, 1.0);
	pos = coords;
}
