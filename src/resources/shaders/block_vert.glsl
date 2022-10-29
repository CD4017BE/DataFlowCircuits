#version 110
uniform mat3 transform;
uniform vec3 texScale;
uniform sampler1D atlas; //(u0, v0, w, h)

attribute vec2 pos, str;
attribute float iconId, corner;
varying vec2 tex0, tex1, f;

const float ROFS = 1.0 / 65536.0, RSCA = ROFS * 65535.0;

void main() {
	gl_Position = vec4(transform * vec3(pos, 1.0), 1.0);
	vec4 tex = texture1D(atlas, iconId * texScale.z) * RSCA + vec4(0, 0, ROFS, ROFS);
	vec2 c = vec2(mod(corner, 2.0), floor(corner / 2.0));
	vec2 ofs = str * texScale.xy;
	tex1 = tex.xy + (tex.zw + ofs) * c;
	tex0 = tex1 - ofs;
	f = (c - 0.5) * tex.zw / max(ofs, texScale.xy) + c;
}
