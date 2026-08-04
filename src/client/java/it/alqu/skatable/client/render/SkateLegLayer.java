package it.alqu.skatable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a skateboarder's legs as smoothly deforming tubes.
 *
 * <p>Minecraft's {@code ModelPart} can only render rigid boxes, so bending a
 * leg through the model system means hinging stiff segments — which either
 * snaps in half or looks like a string of sausages. This layer instead emits
 * geometry directly: the leg is swept along a curved centreline as a stack of
 * quad rings, and the skin's UV coordinates advance continuously down that
 * sweep, so the texture genuinely stretches around the bend.
 *
 * <p>The vanilla leg parts are hidden while this draws (see PlayerModelMixin).
 * Leg armour still renders from its own rigid model, so it will not follow the
 * curve — riders in leggings keep the plain stance.
 */
public class SkateLegLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** Rings along the leg. More is smoother; 12 is well past the point of visible faceting. */
	private static final int RINGS = 12;
	private static final float LEG_LENGTH = 12.0f;
	private static final float HALF_WIDTH = 2.0f;
	/** Skin texture is 64x64. */
	private static final float TEX = 64.0f;
	/** Overlay (trousers) layer sits just outside the skin layer. */
	private static final float OVERLAY_INFLATE = 0.25f;

	public SkateLegLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			AvatarRenderState state, float yRot, float xRot) {
		SkateRideState ride = state.getData(SkateRideState.KEY);
		if (ride == null || state.isInvisible) {
			return;
		}
		PlayerModel model = this.getParentModel();
		Identifier skin = state.skin.body().texturePath();
		var renderType = RenderTypes.entityTranslucent(skin);

		// Skin regions: right leg (0,16) with trousers at (0,32);
		// left leg (16,48) with trousers at (0,48).
		this.submitLeg(poseStack, collector, lightCoords, renderType, model.rightLeg,
				ride.rightLegBend(), 0, 16, 0, 32);
		this.submitLeg(poseStack, collector, lightCoords, renderType, model.leftLeg,
				ride.leftLegBend(), 16, 48, 0, 48);
	}

	private void submitLeg(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			net.minecraft.client.renderer.rendertype.RenderType renderType, ModelPart leg, float bend,
			int legU, int legV, int overlayU, int overlayV) {
		poseStack.pushPose();
		// Step into the leg's own space: origin at the hip, +y running down the leg.
		leg.translateAndRotate(poseStack);
		// Baked model cubes are stored pre-divided by 16, so work in model pixels.
		poseStack.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

		Ring[] rings = buildRings(bend, 0.0f);
		Ring[] overlayRings = buildRings(bend, OVERLAY_INFLATE);

		collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
				emitSweep(pose, buffer, rings, legU, legV, lightCoords));
		collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
				emitSweep(pose, buffer, overlayRings, overlayU, overlayV, lightCoords));

		poseStack.popPose();
	}

	/** One cross-section of the leg: its centre and the axes of its square face. */
	private record Ring(Vector3f centre, Vector3f right, Vector3f forward) {
	}

	/**
	 * How far the foot ends up below the hip once the leg is curved. A bent leg
	 * is shorter than a straight one, so the pose has to sink the whole model by
	 * the difference to keep the feet on the deck.
	 */
	public static float footDrop(float bend) {
		Ring[] rings = buildRings(bend, 0.0f);
		return rings[rings.length - 1].centre().y();
	}

	/**
	 * Sweeps a square cross-section down a curved centreline. The bend is spread
	 * over the rings with a bell weighting so the leg curves through the knee
	 * instead of creasing at one joint.
	 */
	private static Ring[] buildRings(float bend, float inflate) {
		Ring[] rings = new Ring[RINGS + 1];
		float step = LEG_LENGTH / RINGS;
		float half = HALF_WIDTH + inflate;

		// Bell-shaped curvature, normalised so the angles total the full bend.
		float[] curvature = new float[RINGS];
		float total = 0.0f;
		for (int i = 0; i < RINGS; i++) {
			float t = (i + 0.5f) / RINGS;
			// Peak just below the middle, where a knee sits.
			float d = (t - 0.55f) / 0.28f;
			curvature[i] = (float) Math.exp(-d * d);
			total += curvature[i];
		}

		float angle = 0.0f;
		float x = 0.0f;
		float y = -inflate;
		float z = 0.0f;
		for (int i = 0; i <= RINGS; i++) {
			float sin = Mth.sin(angle);
			float cos = Mth.cos(angle);
			// The cross-section stays square, tilted with the centreline.
			rings[i] = new Ring(new Vector3f(x, y, z),
					new Vector3f(half, 0.0f, 0.0f),
					new Vector3f(0.0f, -sin * half, cos * half));
			if (i < RINGS) {
				float advance = step + (i == RINGS - 1 ? inflate * 2.0f : 0.0f);
				y += cos * advance;
				z += sin * advance;
				angle += bend * (curvature[i] / total);
			}
		}
		return rings;
	}

	/**
	 * Emits the four long faces of the swept leg. The V coordinate advances with
	 * the sweep, so the skin follows the curve rather than being cut into chunks.
	 */
	private static void emitSweep(PoseStack.Pose pose, VertexConsumer buffer, Ring[] rings,
			int u0, int v0, int lightCoords) {
		Matrix4f matrix = pose.pose();
		int overlay = OverlayTexture.NO_OVERLAY;
		// Corner offsets, as multiples of the ring's right/forward axes, walking
		// around the cross-section. Each edge is one of the leg's four faces, and
		// each face owns a 4px-wide column of the skin.
		float[][] corners = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
		// Face order around the leg matches the vanilla box unwrap: the faces sit
		// at u0, u0+4, u0+8 and u0+12.
		int[] faceU = { 4, 12, 8, 0 };

		for (int face = 0; face < 4; face++) {
			float[] a = corners[face];
			float[] b = corners[(face + 1) % 4];
			float uLeft = (u0 + faceU[face]) / TEX;
			float uRight = (u0 + faceU[face] + 4) / TEX;

			for (int i = 0; i < rings.length - 1; i++) {
				Ring lower = rings[i];
				Ring upper = rings[i + 1];
				float vLow = (v0 + 4 + LEG_LENGTH * i / RINGS) / TEX;
				float vHigh = (v0 + 4 + LEG_LENGTH * (i + 1) / RINGS) / TEX;

				Vector3f p0 = corner(lower, a);
				Vector3f p1 = corner(lower, b);
				Vector3f p2 = corner(upper, b);
				Vector3f p3 = corner(upper, a);

				// Outward normal for this strip, from the quad's own edges.
				Vector3f normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p3).sub(p0)).normalize();
				pose.transformNormal(normal, normal);

				vertex(buffer, matrix, p0, uLeft, vLow, normal, lightCoords, overlay);
				vertex(buffer, matrix, p3, uLeft, vHigh, normal, lightCoords, overlay);
				vertex(buffer, matrix, p2, uRight, vHigh, normal, lightCoords, overlay);
				vertex(buffer, matrix, p1, uRight, vLow, normal, lightCoords, overlay);
			}
		}

		// Cap the top of the thigh and the sole of the foot so the tube is closed.
		emitCap(pose, buffer, rings[0], u0 + 4, v0, true, lightCoords, overlay);
		emitCap(pose, buffer, rings[rings.length - 1], u0 + 8, v0, false, lightCoords, overlay);
	}

	private static void emitCap(PoseStack.Pose pose, VertexConsumer buffer, Ring ring,
			int u, int v, boolean up, int lightCoords, int overlay) {
		Matrix4f matrix = pose.pose();
		float[][] corners = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
		Vector3f normal = new Vector3f(0.0f, up ? -1.0f : 1.0f, 0.0f);
		pose.transformNormal(normal, normal);
		float uLeft = u / TEX;
		float uRight = (u + 4) / TEX;
		float vTop = v / TEX;
		float vBottom = (v + 4) / TEX;

		Vector3f[] p = new Vector3f[4];
		for (int i = 0; i < 4; i++) {
			p[i] = corner(ring, corners[up ? i : 3 - i]);
		}
		vertex(buffer, matrix, p[0], uLeft, vTop, normal, lightCoords, overlay);
		vertex(buffer, matrix, p[1], uRight, vTop, normal, lightCoords, overlay);
		vertex(buffer, matrix, p[2], uRight, vBottom, normal, lightCoords, overlay);
		vertex(buffer, matrix, p[3], uLeft, vBottom, normal, lightCoords, overlay);
	}

	private static Vector3f corner(Ring ring, float[] offset) {
		return new Vector3f(ring.centre())
				.add(ring.right().x() * offset[0], ring.right().y() * offset[0], ring.right().z() * offset[0])
				.add(ring.forward().x() * offset[1], ring.forward().y() * offset[1], ring.forward().z() * offset[1]);
	}

	private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vector3f pos, float u, float v,
			Vector3f normal, int lightCoords, int overlay) {
		buffer.addVertex(matrix, pos.x(), pos.y(), pos.z())
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(overlay)
				.setLight(lightCoords)
				.setNormal(normal.x(), normal.y(), normal.z());
	}
}
