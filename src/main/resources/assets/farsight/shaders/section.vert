#version 460 core
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
    SectionRecord sec = sections[gl_BaseInstance];
    vec3 origin = sec.aabbMin.xyz;
    float voxelScale = sec.aabbMin.w;

    uvec4 packed = vertices[gl_VertexID];

    uint x = packed.x & 0xFFu;
    uint y = (packed.x >> 8u) & 0xFFu;
    uint z = (packed.x >> 16u) & 0xFFu;
    uint face = (packed.x >> 24u) & 0x7u;

    vec3 local = vec3(x, y, z) * voxelScale;
    vec3 world = origin + local;

    v_worldPos = world;
    v_faceIdx = face;
    v_stateId = packed.z & 0xFFFFFFu;
    v_ao      = packed.y & 0xFFu;
    v_biome   = packed.w & 0xFFFu;

    gl_Position = u_viewProj * vec4(world, 1.0);
}
