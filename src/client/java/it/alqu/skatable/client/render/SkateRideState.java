package it.alqu.skatable.client.render;

import it.alqu.skatable.Trick;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * Skateboarding pose data, attached to the rider's render state so the player
 * model can be posed without needing the entity (render states are decoupled
 * from entities). Populated in {@code AvatarRendererMixin}.
 *
 * @param leanDegrees  how hard the board is carving, signed (right positive)
 * @param speed        horizontal blocks per tick
 * @param grinding     the board is grinding a rail
 * @param airborne     the board is off the ground
 * @param trick        the trick being animated, or null
 * @param trickTime    0..1 progress through that trick
 * @param goofy        rider stands goofy (right foot forward) instead of regular
 */
public record SkateRideState(float leanDegrees, float speed, boolean grinding, boolean airborne,
		Trick trick, float trickTime, boolean goofy) {
	public static final RenderStateDataKey<SkateRideState> KEY = RenderStateDataKey.create();
}
