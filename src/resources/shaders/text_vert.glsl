#version 150 core
uniform mat3x4 transform;
uniform vec2 gridSize;
uniform vec4 tileSize;
uniform uint tileStride;
uniform isamplerBuffer origin; // ivec4(startX, startY, lineWrap, startIdx)
in uint index;
in uint charCode;
out vec2 uv0;

void main() {
	ivec4 o = texelFetch(origin, int(index));
	int i = gl_VertexID - o.w;
	gl_Position = transform * vec3(vec2(o.xy) * gridSize + vec2(i % o.z, i / o.z), 1.0);
	uv0 = vec2(charCode % tileStride, charCode / tileStride) * tileSize.st + tileSize.pq;
}