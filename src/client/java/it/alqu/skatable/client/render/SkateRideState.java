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

	/**
	 * Total knee bend, in radians, handed to the leg mesh. The front leg carries
	 * the deeper bend; both deepen as the rider crouches.
	 */
	public float bend(boolean front) {
		float crouch = Math.min(0.3f + 0.4f * Math.min(this.speed / 0.7f, 1.0f)
				+ (this.grinding ? 0.3f : 0.0f), 1.0f);
		float total = (float) Math.toRadians(22.0 + 48.0 * crouch);
		return front ? total : total * 0.7f;
	}

	public float leftLegBend() {
		return this.bend(!this.goofy);
	}

	public float rightLegBend() {
		return this.bend(this.goofy);
	}
}
