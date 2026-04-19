#version 430 core

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
    switch (int(face)) {
        case 0: case 1: return 0.75;   // -X / +X
        case 2:         return 0.55;   // -Y (bottom)
        case 3:         return 1.00;   // +Y (top)
        case 4: case 5: return 0.85;   // -Z / +Z
        default:        return 1.00;
    }
}

void main() {
    vec3 base = hashColor(v_stateId);
    float ao = float(v_ao) / 255.0;
    vec3 lit = base * faceLight(v_faceIdx) * (0.5 + 0.5 * ao);

    float d = length(v_worldPos - u_cameraPos);
    float fogT = clamp((d - u_fogStart) / max(1.0, (u_fogEnd - u_fogStart)), 0.0, 1.0);
    vec3 finalColor = mix(lit, u_fogColor, fogT);

    fragColor = vec4(finalColor, 1.0);
}
