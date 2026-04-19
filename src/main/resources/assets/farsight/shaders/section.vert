#version 430 core
#extension GL_ARB_shader_draw_parameters : require

// Per-vertex packed data. Mega-VBO shared across all sections; baseVertex
// on the indirect draw command offsets gl_VertexID into this section's
// block, so this binding can be the entire pool.
layout(std430, binding = 0) readonly buffer VertexData {
    uvec4 vertices[];
};

struct SectionRecord {
    vec4 aabbMin;   // xyz = world origin, w = voxelScale
    vec4 aabbMax;   // xyz = world origin + extent, w = floatBitsToUint(baseVertex)
};

// Section table — produced by SectionRegistry on the CPU and indexed on the
// GPU by gl_BaseInstance (set by CullingCompute when compacting survivors
// into the indirect-draw buffer).
layout(std430, binding = 2) readonly buffer SectionInfos {
    SectionRecord sections[];
};

uniform mat4 u_viewProj;

flat out uint v_stateId;
flat out uint v_faceIdx;
flat out uint v_ao;
flat out uint v_biome;
out vec3 v_worldPos;

void main() {
    SectionRecord sec = sections[gl_BaseInstanceARB];
    vec3 origin = sec.aabbMin.xyz;
    float voxelScale = sec.aabbMin.w;

    uvec4 packedData = vertices[gl_VertexID];

    uint px = packedData.x & 0xFFu;
    uint py = (packedData.x >> 8u) & 0xFFu;
    uint pz = (packedData.x >> 16u) & 0xFFu;
    uint pFace = (packedData.x >> 24u) & 0x7u;

    vec3 local = vec3(px, py, pz) * voxelScale;
    vec3 world = origin + local;

    v_worldPos = world;
    v_faceIdx = pFace;
    v_stateId = packedData.z & 0xFFFFFFu;
    v_ao      = packedData.y & 0xFFu;
    v_biome   = packedData.w & 0xFFFu;

    gl_Position = u_viewProj * vec4(world, 1.0);
}
