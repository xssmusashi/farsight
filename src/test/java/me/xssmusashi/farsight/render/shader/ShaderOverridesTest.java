package me.xssmusashi.farsight.render.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShaderOverridesTest {

    @Test
    void fallsBackToBundledWhenNoOverride(@TempDir Path tmp) {
        String src = ShaderOverrides.load(null, ShaderOverrides.VERT_FILE,
            "/assets/farsight/shaders/section.vert");
        assertTrue(src.contains("#version"));
    }

    @Test
    void fallsBackToBundledWhenRootExistsButFileMissing(@TempDir Path tmp) {
        String src = ShaderOverrides.load(tmp, ShaderOverrides.VERT_FILE,
            "/assets/farsight/shaders/section.vert");
        assertTrue(src.contains("#version"));
    }

    @Test
    void prefersPackOverride(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve(ShaderOverrides.SUBDIR);
        Files.createDirectories(dir);
        Path override = dir.resolve(ShaderOverrides.VERT_FILE);
        Files.writeString(override, "// pack-provided\n#version 460 core\nvoid main(){}");
        String src = ShaderOverrides.load(tmp, ShaderOverrides.VERT_FILE,
            "/assets/farsight/shaders/section.vert");
        assertTrue(src.contains("pack-provided"));
    }
}
