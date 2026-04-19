package me.xssmusashi.farsight.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No GL context is available during unit tests, so we can only verify that
 * the shader source files are packaged and readable. A real GL compile check
 * has to happen at runtime — see {@code FarsightRenderer.ensureInitialised}.
 */
class ShaderResourcesTest {

    @Test
    void allShaderResourcesLoad() {
        String vert = ShaderProgram.loadResource("/assets/farsight/shaders/section.vert");
        String frag = ShaderProgram.loadResource("/assets/farsight/shaders/section.frag");
        String cull = ShaderProgram.loadResource("/assets/farsight/shaders/culling.comp");
        String hiz = ShaderProgram.loadResource("/assets/farsight/shaders/hiz_build.comp");
        assertTrue(vert.contains("#version 460"));
        assertTrue(frag.contains("#version 460"));
        assertTrue(cull.contains("#version 460") && cull.contains("layout(local_size_x"));
        assertTrue(hiz.contains("#version 460") && hiz.contains("layout(local_size_x"));
    }
}
