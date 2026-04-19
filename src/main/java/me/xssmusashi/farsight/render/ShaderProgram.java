package me.xssmusashi.farsight.render;

import me.xssmusashi.farsight.FarsightClient;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL46;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Wraps an OpenGL 4.6 shader program. Compile + link happen in the
 * constructor; {@link #close()} must be called to release the program.
 */
public final class ShaderProgram implements AutoCloseable {
    private final int program;

    private ShaderProgram(int program) {
        this.program = program;
    }

    public int id() { return program; }

    public void use() { GL46.glUseProgram(program); }

    public int uniformLocation(String name) {
        int loc = GL46.glGetUniformLocation(program, name);
        if (loc < 0) {
            FarsightClient.LOGGER.warn("uniform '{}' not found (optimised out?)", name);
        }
        return loc;
    }

    @Override
    public void close() {
        GL46.glDeleteProgram(program);
    }

    public static ShaderProgram graphics(String vertSrc, String fragSrc) {
        int v = compile(GL46.GL_VERTEX_SHADER, vertSrc, "vertex");
        int f = compile(GL46.GL_FRAGMENT_SHADER, fragSrc, "fragment");
        int prog = GL46.glCreateProgram();
        GL46.glAttachShader(prog, v);
        GL46.glAttachShader(prog, f);
        GL46.glLinkProgram(prog);
        checkLink(prog);
        GL46.glDetachShader(prog, v);
        GL46.glDetachShader(prog, f);
        GL46.glDeleteShader(v);
        GL46.glDeleteShader(f);
        return new ShaderProgram(prog);
    }

    public static ShaderProgram compute(String computeSrc) {
        int c = compile(GL43.GL_COMPUTE_SHADER, computeSrc, "compute");
        int prog = GL46.glCreateProgram();
        GL46.glAttachShader(prog, c);
        GL46.glLinkProgram(prog);
        checkLink(prog);
        GL46.glDetachShader(prog, c);
        GL46.glDeleteShader(c);
        return new ShaderProgram(prog);
    }

    private static int compile(int type, String src, String label) {
        int id = GL46.glCreateShader(type);
        GL46.glShaderSource(id, src);
        GL46.glCompileShader(id);
        if (GL46.glGetShaderi(id, GL46.GL_COMPILE_STATUS) == GL46.GL_FALSE) {
            String log = GL46.glGetShaderInfoLog(id);
            GL46.glDeleteShader(id);
            throw new IllegalStateException(label + " shader compile failed:\n" + log);
        }
        return id;
    }

    private static void checkLink(int prog) {
        if (GL46.glGetProgrami(prog, GL46.GL_LINK_STATUS) == GL46.GL_FALSE) {
            String log = GL46.glGetProgramInfoLog(prog);
            GL46.glDeleteProgram(prog);
            throw new IllegalStateException("program link failed:\n" + log);
        }
    }

    /** Loads a shader source file from the mod's classpath resources. */
    public static String loadResource(String path) {
        try (InputStream in = ShaderProgram.class.getResourceAsStream(path)) {
            Objects.requireNonNull(in, "shader resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + path, e);
        }
    }
}
