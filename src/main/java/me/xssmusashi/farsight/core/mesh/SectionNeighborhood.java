package me.xssmusashi.farsight.core.mesh;

import me.xssmusashi.farsight.core.voxel.Section;

/**
 * One native section plus references to the 6 axis-aligned neighbour sections,
 * used by the mesher to avoid emitting outer-boundary faces against
 * already-loaded neighbours, and to produce boundary skirts at LoD
 * transitions.
 *
 * <p>Semantics:
 * <ul>
 *   <li>When a neighbour on a given face is {@code null}, the mesher treats
 *       that boundary as exposed to air — outer faces are emitted.</li>
 *   <li>When the neighbour is present and at the <em>same</em> LoD level,
 *       the two sections share a boundary plane; faces whose neighbour
 *       voxel is solid are culled.</li>
 *   <li>When the neighbour is at a <em>lower</em> LoD level (a coarser
 *       parent), the mesher should extend the quad 1 voxel into the parent
 *       to form a skirt that hides seams. This is not yet implemented —
 *       see the TODO in {@link GreedyMesher}.</li>
 * </ul>
 * </p>
 */
public record SectionNeighborhood(
        Section self,
        Section negX, Section posX,
        Section negY, Section posY,
        Section negZ, Section posZ,
        boolean negXLower, boolean posXLower,
        boolean negYLower, boolean posYLower,
        boolean negZLower, boolean posZLower) {

    public static SectionNeighborhood isolated(Section self) {
        return new SectionNeighborhood(
            self, null, null, null, null, null, null,
            false, false, false, false, false, false);
    }

    public boolean hasLowerLodNeighbour() {
        return negXLower || posXLower || negYLower || posYLower || negZLower || posZLower;
    }
}
