#version 110
uniform mat3 transform;
uniform vec3 texScale;
uniform sampler1D atlas; //(u0, v0, w, h)@i+0, (ru0, rv0, ru1, rv1)@i+0.5

attribute vec2 pos, str;
attribute float iconId, corner;
varying vec2 tex0, tex1, size, ofs;

const float ROFS = 1.0 / 65536.0, RSCA = ROFS * 65535.0;

void main() {
	gl_Position = vec4(transform * vec3(pos, 1.0), 1.0);
	vec2 strSca = str * texScale.xy;
	vec4 tex = texture1D(atlas, iconId * texScale.z) * RSCA;
	vec2 iconSize = tex.zw + vec2(ROFS);
	vec4 bounds = texture1D(atlas, (iconId + 0.5) * texScale.z) * RSCA * iconSize.xyxy;
	vec2 uv = vec2(mod(corner, 2.0), floor(corner / 2.0)) * (iconSize + strSca);
	size = bounds.zw - bounds.xy;
	ofs = tex.xy + bounds.xy;
	tex0 = uv - bounds.xy;
	tex1 = uv - bounds.zw - strSca;
}
