package me.xssmusashi.farsight.render.shader;

import me.xssmusashi.farsight.FarsightClient;

import java.lang.reflect.Method;

/**
 * Reflection-only probe for the Iris shader loader. Farsight does <em>not</em>
 * take a compile-time dependency on Iris — the mod must work in installs
 * without it. This class resolves Iris's public API classes at runtime and
 * caches the result; if resolution fails the compatibility layer is
 * permanently inactive for the session.
 *
 * <p>Iris exposes a stable entry point at {@code net.irisshaders.iris.api.v0
 * .IrisApi#getInstance()}, with {@code isShaderPackInUse()} and
 * {@code getCurrentShaderpack()} accessors. That is the entirety of the
 * public surface Farsight depends on for detection; render-pass integration
 * uses internal APIs that can change and therefore lives behind
 * {@link IrisAdapter}.</p>
 */
public final class IrisCompatibility {
    private static final IrisCompatibility INSTANCE = new IrisCompatibility();

    public static IrisCompatibility get() { return INSTANCE; }

    private final boolean classesPresent;
    private final Object irisApiInstance;
    private final Method isShaderPackInUse;
    private final Method getCurrentShaderpackName;

    private IrisCompatibility() {
        boolean present = false;
        Object instance = null;
        Method inUse = null;
        Method name = null;
        try {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstance = irisApi.getMethod("getInstance");
            instance = getInstance.invoke(null);
            inUse = irisApi.getMethod("isShaderPackInUse");
            try {
                name = irisApi.getMethod("getCurrentShaderpack");
            } catch (NoSuchMethodException e) {
                name = null;  // older Iris versions may not expose the name
            }
            present = true;
            FarsightClient.LOGGER.info("Iris detected — compatibility probe active");
        } catch (ClassNotFoundException e) {
            FarsightClient.LOGGER.info("Iris not detected — compatibility layer stays inactive");
        } catch (ReflectiveOperationException e) {
            FarsightClient.LOGGER.warn("Iris present but API probe failed — compatibility disabled", e);
        }
        this.classesPresent = present;
        this.irisApiInstance = instance;
        this.isShaderPackInUse = inUse;
        this.getCurrentShaderpackName = name;
    }

    /** {@code true} when an Iris installation has been found at runtime. */
    public boolean isInstalled() { return classesPresent; }

    /**
     * {@code true} when Iris is installed <em>and</em> the user has an active
     * shader pack loaded — i.e. Farsight must coordinate with it instead of
     * running its own deferred pipeline.
     */
    public boolean isShaderPackActive() {
        if (!classesPresent) return false;
        try {
            return (Boolean) isShaderPackInUse.invoke(irisApiInstance);
        } catch (ReflectiveOperationException e) {
            FarsightClient.LOGGER.debug("isShaderPackInUse threw", e);
            return false;
        }
    }

    /** Best-effort shader pack name; {@code null} if Iris is inactive or unsupported. */
    public String activeShaderPackName() {
        if (!classesPresent || getCurrentShaderpackName == null) return null;
        try {
            Object v = getCurrentShaderpackName.invoke(irisApiInstance);
            return v == null ? null : v.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
