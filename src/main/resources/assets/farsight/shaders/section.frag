#version 330 core

flat in uint v_stateId;
flat in uint v_faceIdx;
flat in uint v_ao;
flat in uint v_biome;
in vec3 v_worldPos;

uniform vec3  u_cameraPos;
uniform vec3  u_fogColor;
uniform float u_fogStart;
uniform float u_fogEnd;

out vec4 fragColor;

vec3 hashColor(uint id) {
    float r = fract(float(id) * 0.61803398875);
    float g = fract(float(id) * 0.32471795724);
    float b = fract(float(id) * 0.73216813908);
    return vec3(r, g, b);
}

float faceLight(uint face) {
    if (face == 0u || face == 1u) return 0.75;
    if (face == 2u) return 0.55;
    if (face == 3u) return 1.00;
    if (face == 4u || face == 5u) return 0.85;
    return 1.00;
}

void main() {
    // DEBUG: saturated high-chroma colour so any visible pixel is obvious.
    vec3 base = hashColor(v_stateId);
    fragColor = vec4(max(base, vec3(0.3)) * faceLight(v_faceIdx), 1.0);
}
