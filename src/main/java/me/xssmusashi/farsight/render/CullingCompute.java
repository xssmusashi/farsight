package me.xssmusashi.farsight.render;

import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL46;

/**
 * Dispatches the frustum + Hi-Z culling compute shader. Inputs the list of
 * section AABBs, outputs a dense list of indirect-draw commands plus a
 * counter holding the surviving section count.
 */
public final class CullingCompute implements AutoCloseable {
    private final ShaderProgram program;
    private final int localSizeX;

    public CullingCompute() {
        this.program = ShaderProgram.compute(
            ShaderProgram.loadResource("/assets/farsight/shaders/culling.comp"));
        this.localSizeX = 64;
    }

    public void dispatch(int sectionCount, int sectionsSsbo, int commandsSsbo,
                         int counterSsbo, int hiZTex, int hiZWidth, int hiZHeight,
                         float[] viewProjMat4x4) {
        program.use();
        int groups = (sectionCount + localSizeX - 1) / localSizeX;

        GL46.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, sectionsSsbo);
        GL46.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, commandsSsbo);
        GL46.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, counterSsbo);

        GL46.glActiveTexture(GL46.GL_TEXTURE3);
        GL46.glBindTexture(GL46.GL_TEXTURE_2D, hiZTex);
        GL46.glUniform1i(program.uniformLocation("u_hiZ"), 3);
        GL46.glUniform1ui(program.uniformLocation("u_sectionCount"), sectionCount);
        GL46.glUniform2i(program.uniformLocation("u_hiZSize"), hiZWidth, hiZHeight);
        GL46.glUniformMatrix4fv(program.uniformLocation("u_viewProj"), false, viewProjMat4x4);

        GL46.glDispatchCompute(groups, 1, 1);
        GL46.glMemoryBarrier(
            GL43.GL_COMMAND_BARRIER_BIT |
            GL43.GL_SHADER_STORAGE_BARRIER_BIT |
            GL46.GL_ATOMIC_COUNTER_BARRIER_BIT);
    }

    @Override
    public void close() {
        program.close();
    }
}
