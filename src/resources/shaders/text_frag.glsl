#version 110
uniform sampler2D font;

varying vec2 fontUV;
varying vec4 fgColor, bgColor;

void main() {
	float x = texture2D(font, fontUV).r;
	float y = clamp(x + fgColor.a - bgColor.a, 0.0, 1.0);
	gl_FragColor = mix(bgColor, fgColor, vec4(y, y, y, x));
}