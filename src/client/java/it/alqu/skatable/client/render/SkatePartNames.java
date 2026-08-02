package it.alqu.skatable.client.render;

/**
 * Names of the extra leg parts Skatable adds to the player model so the knee
 * can bend. Kept outside the mixin classes, which may not expose non-private
 * static fields.
 *
 * <p>The leg is sliced into {@link #SEGMENTS} stacked pieces rather than two
 * halves: Minecraft renders rigid boxes with no vertex skinning, so a single
 * hinge leaves a visible wedge. Spreading the same total angle over several
 * small joints approximates a curve, and the slices are thin enough (plus
 * slightly inflated) that the seams close up.
 */
public final class SkatePartNames {
	/** Slices per leg. 12 model pixels divide evenly by 4, keeping texels aligned. */
	public static final int SEGMENTS = 4;
	/** Height of one slice, in model pixels. */
	public static final int SEGMENT_HEIGHT = 12 / SEGMENTS;

	public static String segment(int index) {
		return "skatable_leg_" + index;
	}

	public static String segmentOverlay(int index) {
		return "skatable_leg_overlay_" + index;
	}

	private SkatePartNames() {
	}
}
