package me.xssmusashi.farsight.boot;

import sun.misc.Unsafe;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Self-injecting module opener. At mod init time (before any lmdbjava class
 * is touched) we use the {@code jdk.unsupported}-exported
 * {@link sun.misc.Unsafe} instance to forcibly flip the {@code override}
 * flag on {@code Module.implAddOpens(String)} and then invoke it on
 * {@code java.base} to open {@code java.nio} / {@code sun.nio.ch} to all
 * unnamed modules — the same effect as passing
 * {@code --add-opens java.base/java.nio=ALL-UNNAMED} on the command line,
 * without actually requiring that argument.
 *
 * <p>This is not a public API of the JVM — it relies on:
 * <ul>
 *   <li>the {@code sun.misc.Unsafe.theUnsafe} field being reachable through
 *       reflection (Fabric already opens {@code sun.misc} for unnamed, and
 *       {@code jdk.unsupported} re-exports it);</li>
 *   <li>{@code AccessibleObject.override} existing as a boolean field
 *       (stable since JDK 8);</li>
 *   <li>{@code Module.implAddOpens(String)} existing (stable since JDK 9).</li>
 * </ul>
 * If any of that drifts in a future JDK, the method logs and returns — the
 * caller sees {@link #succeeded()} {@code == false} and can fall back.</p>
 */
public final class ModuleUnlocker {
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);
    private static volatile boolean succeeded = false;

    private ModuleUnlocker() {}

    public static boolean succeeded() { return succeeded; }

    /** Idempotent. Safe to call more than once; only the first call does work. */
    public static synchronized boolean unlockJavaNio() {
        if (!ATTEMPTED.compareAndSet(false, true)) return succeeded;
        try {
            Unsafe unsafe = fetchUnsafe();

            Field overrideField = findOverrideField();
            long overrideOffset = unsafe.objectFieldOffset(overrideField);

            Method implAddOpens = Module.class.getDeclaredMethod("implAddOpens", String.class);
            unsafe.putBoolean(implAddOpens, overrideOffset, true);

            Module base = Buffer.class.getModule();
            implAddOpens.invoke(base, "java.nio");
            implAddOpens.invoke(base, "sun.nio.ch");

            succeeded = true;
            return true;
        } catch (Throwable t) {
            System.err.println("[Farsight] ModuleUnlocker failed — lmdbjava will need `--add-opens java.base/java.nio=ALL-UNNAMED` in JVM args. Cause: " + t);
            return false;
        }
    }

    private static Unsafe fetchUnsafe() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    private static Field findOverrideField() throws NoSuchFieldException {
        // JDK 8+: AccessibleObject.override
        try {
            return AccessibleObject.class.getDeclaredField("override");
        } catch (NoSuchFieldException ignored) {}
        // Some future JDK may rename it. Scan for a boolean field.
        for (Field f : AccessibleObject.class.getDeclaredFields()) {
            if (f.getType() == boolean.class) return f;
        }
        throw new NoSuchFieldException("no boolean field on AccessibleObject");
    }
}
