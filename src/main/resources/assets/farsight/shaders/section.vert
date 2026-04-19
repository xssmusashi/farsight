#version 460 core

layout(std430, binding = 0) readonly buffer VertexData {
    uvec4 vertices[];
};

uniform mat4 u_viewProj;
uniform ivec3 u_sectionOrigin;
uniform float u_voxelScale;

flat out uint v_stateId;
flat out uint v_faceIdx;
flat out uint v_ao;
flat out uint v_biome;
out vec3 v_worldPos;

void main() {
    uvec4 packed = vertices[gl_VertexID];

    uint x = packed.x & 0xFFu;
    uint y = (packed.x >> 8u) & 0xFFu;
    uint z = (packed.x >> 16u) & 0xFFu;
    uint face = (packed.x >> 24u) & 0x7u;

    vec3 local = vec3(x, y, z) * u_voxelScale;
    vec3 world = vec3(u_sectionOrigin) + local;

    v_worldPos = world;
    v_faceIdx = face;
    v_stateId = packed.z & 0xFFFFFFu;
    v_ao      = packed.y & 0xFFu;
    v_biome   = packed.w & 0xFFFu;

    gl_Position = u_viewProj * vec4(world, 1.0);
}
