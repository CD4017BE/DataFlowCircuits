#version 150 core
uniform sampler2D font;
uniform vec4 fgColor, bgColor;
in vec2 uv;
out vec4 outColor;

void main() {
	outColor = mix(bgColor, fgColor, texture(font, uv).r);
}