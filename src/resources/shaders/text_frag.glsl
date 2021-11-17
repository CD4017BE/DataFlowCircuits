#version 150 core
uniform sampler2D font;
uniform vec4 fgColor, bgColor;
in vec2 uv;
out vec4 outColor;

void main() {
	float x = texture(font, uv).r;
	float y = clamp(x + fgColor.a - bgColor.a, 0.0, 1.0);
	outColor = mix(bgColor, fgColor, vec4(y, y, y, x));
}