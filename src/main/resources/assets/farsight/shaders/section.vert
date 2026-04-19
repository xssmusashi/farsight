#version 330 core

// Per-vertex packed data fed via standard vertex attribute (no SSBO) for
// maximum compatibility with MC's GL 3.3 core context on drivers that don't
// expose 4.3+ features.
layout(location = 0) in uvec4 a_packed;

uniform mat4 u_viewProj;
uniform vec3 u_sectionOrigin;
uniform float u_voxelScale;

flat out uint v_stateId;
flat out uint v_faceIdx;
flat out uint v_ao;
flat out uint v_biome;
out vec3 v_worldPos;

void main() {
    uint px = a_packed.x & 0xFFu;
    uint py = (a_packed.x >> 8u) & 0xFFu;
    uint pz = (a_packed.x >> 16u) & 0xFFu;
    uint pFace = (a_packed.x >> 24u) & 0x7u;

    vec3 local = vec3(px, py, pz) * u_voxelScale;
    vec3 world = u_sectionOrigin + local;

    v_worldPos = world;
    v_faceIdx = pFace;
    v_stateId = a_packed.z & 0xFFFFFFu;
    v_ao      = a_packed.y & 0xFFu;
    v_biome   = a_packed.w & 0xFFFu;

    gl_Position = u_viewProj * vec4(world, 1.0);
}
