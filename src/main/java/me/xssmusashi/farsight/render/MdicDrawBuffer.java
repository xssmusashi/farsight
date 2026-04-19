package me.xssmusashi.farsight.render;

import org.lwjgl.opengl.GL46;

/**
 * Indirect-draw command buffer fed by the culling compute shader. Layout is
 * one {@code DrawElementsIndirectCommand} per slot (20 bytes each). A single
 * {@code uint} counter follows, holding the number of commands the culling
 * pass actually wrote.
 */
public final class MdicDrawBuffer implements AutoCloseable {
    public static final int COMMAND_BYTES = 5 * 4; // count, instanceCount, firstIndex, baseVertex, baseInstance
    public static final int COUNTER_BYTES = 4;

    private final PersistentMappedBuffer commands;
    private final PersistentMappedBuffer counter;
    private final int maxCommands;

    public MdicDrawBuffer(int maxCommands) {
        this.maxCommands = maxCommands;
        long commandsSize = (long) maxCommands * COMMAND_BYTES;
        this.commands = new PersistentMappedBuffer(GL46.GL_DRAW_INDIRECT_BUFFER, commandsSize);
        this.counter = new PersistentMappedBuffer(GL46.GL_PARAMETER_BUFFER, COUNTER_BYTES);
    }

    public int commandBufferId() { return commands.id(); }
    public int counterBufferId() { return counter.id(); }
    public int maxCommands() { return maxCommands; }

    @Override
    public void close() {
        commands.close();
        counter.close();
    }
}
