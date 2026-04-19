#version 330 core

flat in uint v_stateId;
flat in uint v_faceIdx;
flat in uint v_ao;
flat in uint v_biome;
in vec3 v_worldPos;

out vec4 fragColor;

void main() {
    // ABSOLUTE VISIBILITY TEST — pure red. If you see nothing red over Sodium,
    // either the draw never reaches the backbuffer (Iris shader pack owns it)
    // or the geometry is clipped out of frustum.
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
