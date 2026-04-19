package me.xssmusashi.farsight.render.shader;

import me.xssmusashi.farsight.render.ShaderProgram;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Looks for shader-pack overrides that customize Farsight's LoD rendering.
 * A pack declares overrides by placing files at:
 * <pre>
 *   shaders/farsight/gbuffers_farsight_lod.vsh
 *   shaders/farsight/gbuffers_farsight_lod.fsh
 *   shaders/farsight/farsight_cull.csh
 * </pre>
 *
 * <p>If a file is present the pack's version is preferred; otherwise the
 * built-in shader bundled in Farsight's own resources is used. This is the
 * same convention Iris uses for its {@code gbuffers_*} stages.</p>
 *
 * <p>Resolution happens per-render-pass so that hot-swapping shader packs
 * inside Iris is picked up without a game restart.</p>
 */
public final class ShaderOverrides {
    public static final String VERT_FILE = "gbuffers_farsight_lod.vsh";
    public static final String FRAG_FILE = "gbuffers_farsight_lod.fsh";
    public static final String CULL_FILE = "farsight_cull.csh";
    public static final String SUBDIR = "farsight";

    private ShaderOverrides() {}

    /**
     * Loads a shader source, preferring the pack override at {@code shadersRoot/farsight/<file>}
     * and falling back to the built-in resource path {@code /assets/farsight/shaders/<fallback>}.
     *
     * @param shadersRoot absolute path of the currently-active shader pack's
     *                    {@code shaders/} directory, or {@code null} when no
     *                    pack is active
     */
    public static String load(Path shadersRoot, String overrideFile, String fallbackResource) {
        if (shadersRoot != null) {
            Path candidate = shadersRoot.resolve(SUBDIR).resolve(overrideFile);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (Exception ignored) {
                    // Fall through to built-in
                }
            }
        }
        return ShaderProgram.loadResource(fallbackResource);
    }
}
