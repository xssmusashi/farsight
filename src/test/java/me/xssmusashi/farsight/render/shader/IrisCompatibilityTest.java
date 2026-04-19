package me.xssmusashi.farsight.render.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs in a test classpath that does not include Iris. Verifies the probe
 * correctly degrades to "inactive" instead of throwing.
 */
class IrisCompatibilityTest {

    @Test
    void notInstalledInTestClasspath() {
        IrisCompatibility c = IrisCompatibility.get();
        assertFalse(c.isInstalled(),
            "Iris should not be on the test classpath — probe must report absent");
        assertFalse(c.isShaderPackActive());
        assertNull(c.activeShaderPackName());
    }

    @Test
    void adapterResolvesToInactiveWhenIrisAbsent() {
        IrisAdapter a = IrisAdapter.resolve();
        assertFalse(a.isActive());
        assertEquals(0, a.gbufferFboId());
        assertEquals(0, a.terrainProgramId());
        assertEquals(1, a.colorAttachmentCount());
        assertInstanceOf(InactiveIrisAdapter.class, a);
    }
}
