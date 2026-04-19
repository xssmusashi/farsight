package me.xssmusashi.farsight.render.shader;

import me.xssmusashi.farsight.FarsightClient;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Detects shader-pack reloads by polling the Iris {@code PipelineManager}
 * once per render tick and diffing the returned pipeline reference. Iris
 * does not expose a public reload event in 1.10.x; polling object identity
 * is the cheapest correct signal until one lands upstream.
 *
 * <p>{@link #defaultInstance()} builds a supplier that walks
 * {@code Iris.getPipelineManager().getPipelineNullable()} via reflection so
 * this class never triggers Iris class loading — safe to hold a reference
 * to it whether or not Iris is installed.</p>
 */
public final class PipelineWatcher {
    private final Supplier<Object> pipelineSupplier;
    private Object lastPipeline;

    public PipelineWatcher(Supplier<Object> pipelineSupplier) {
        this.pipelineSupplier = pipelineSupplier;
    }

    public static PipelineWatcher defaultInstance() {
        return new PipelineWatcher(PipelineWatcher::probePipeline);
    }

    /** Returns {@code true} when the pipeline identity changed since the previous tick. */
    public boolean tick() {
        Object current;
        try {
            current = pipelineSupplier.get();
        } catch (Throwable t) {
            FarsightClient.LOGGER.debug("pipeline supplier threw", t);
            return false;
        }
        if (current != lastPipeline) {
            lastPipeline = current;
            return true;
        }
        return false;
    }

    public Object lastPipeline() { return lastPipeline; }

    private static Object probePipeline() {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Method getPipelineManager = iris.getMethod("getPipelineManager");
            Object pm = getPipelineManager.invoke(null);
            if (pm == null) return null;
            Method getPipelineNullable = pm.getClass().getMethod("getPipelineNullable");
            return getPipelineNullable.invoke(pm);
        } catch (ClassNotFoundException e) {
            return null;  // Iris not installed — no pipeline
        } catch (ReflectiveOperationException e) {
            FarsightClient.LOGGER.debug("pipeline reflection threw", e);
            return null;
        }
    }
}
