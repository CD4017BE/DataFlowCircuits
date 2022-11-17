#version 110
uniform mat3 transform;
uniform vec4 tileSize;
uniform sampler1D palette;

attribute vec2 pos;
attribute float char, color, corner;

varying vec2 fontUV;
varying vec4 fgColor, bgColor;

void main() {
	gl_Position = vec4(transform * vec3(pos, 1.0), 1.0);
	float tileStride = ceil(1.0 / tileSize.x);
	float v = floor(char / tileStride), u = char - v * tileStride;
	float cy = floor(corner / 2.0), cx = corner - cy * 2.0;
	fontUV = vec2(u + cx, v + cy) * tileSize.xy + tileSize.zw;
	float bgi = floor(color / 32.0), fgi = floor(color - bgi * 32.0);
	fgColor = texture1D(palette, fgi / 32.0);
	bgColor = texture1D(palette, bgi / 32.0);
}