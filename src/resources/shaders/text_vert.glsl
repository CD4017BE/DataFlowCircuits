#version 150 core
uniform mat3x4 transform;
uniform vec4 tileSize;
uniform uint tileStride;
uniform uint lineWrap;
in uint charCode;
out vec2 uv0;

void main() {
	uint i = uint(gl_VertexID);
	gl_Position = transform * vec3(i % lineWrap, i / lineWrap, 1.0);
	uv0 = vec2(charCode % tileStride, charCode / tileStride) * tileSize.st + tileSize.pq;
}