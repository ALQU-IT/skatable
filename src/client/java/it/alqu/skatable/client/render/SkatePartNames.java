package it.alqu.skatable.client.render;

/**
 * Names of the extra leg parts Skatable adds to the player model so the knee
 * can bend. Kept outside the mixin classes, which may not expose non-private
 * static fields.
 */
public final class SkatePartNames {
	public static final String THIGH = "skatable_thigh";
	public static final String SHIN = "skatable_shin";
	public static final String THIGH_OVERLAY = "skatable_thigh_overlay";
	public static final String SHIN_OVERLAY = "skatable_shin_overlay";

	private SkatePartNames() {
	}
}
