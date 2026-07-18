package it.alqu.skatable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.alqu.skatable.Skatable;
import it.alqu.skatable.Trick;
import it.alqu.skatable.entity.SkateboardEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class SkateboardRenderer extends EntityRenderer<SkateboardEntity, SkateboardRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Skatable.id("skateboard"), "main");
	private static final Identifier TEXTURE = Skatable.id("textures/entity/skateboard.png");
	private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

	private final SkateboardModel model;
	private final BlockModelResolver blockModelResolver;

	public SkateboardRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.4f;
		this.model = new SkateboardModel(context.bakeLayer(LAYER));
		this.blockModelResolver = context.getBlockModelResolver();
	}

	@Override
	public SkateboardRenderState createRenderState() {
		return new SkateboardRenderState();
	}

	@Override
	public void extractRenderState(SkateboardEntity entity, SkateboardRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.hurtTime = entity.getHurtTime() - partialTicks;
		state.hurtDir = entity.getHurtDir();
		state.damageTime = Math.max(entity.getDamage() - partialTicks, 0.0f);
		state.grinding = entity.isGrinding();
		state.wheelRoll = Mth.lerp(partialTicks, entity.wheelRollO, entity.wheelRoll);
		if (entity.animTicks > 0 && entity.animTrick != null && entity.animTrick != Trick.OLLIE) {
			state.trick = entity.animTrick;
			state.trickProgress = Mth.clamp(1.0f - (entity.animTicks - partialTicks) / entity.animDuration, 0.0f, 1.0f);
		} else {
			state.trick = null;
			state.trickProgress = 0.0f;
		}
		this.blockModelResolver.update(state.deckModel, entity.getDeckBlock().defaultBlockState(), BLOCK_DISPLAY_CONTEXT);
	}

	@Override
	public void submit(SkateboardRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - state.yRot));

		if (state.hurtTime > 0.0f) {
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10.0f * state.hurtDir));
		}

		if (state.trick != null) {
			float progress = state.trickProgress;
			switch (state.trick) {
				case KICKFLIP -> poseStack.mulPose(Axis.ZP.rotationDegrees(360.0f * progress));
				case HEELFLIP -> poseStack.mulPose(Axis.ZN.rotationDegrees(360.0f * progress));
				case POP_SHOVE_IT -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f * progress));
				case SPIN_360 -> poseStack.mulPose(Axis.YP.rotationDegrees(360.0f * progress));
				default -> {
				}
			}
		}
		if (state.grinding) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(state.ageInTicks * 0.6f) * 2.0f));
		}

		// Deck: the deck material's own block model, squashed into board proportions.
		poseStack.pushPose();
		poseStack.translate(0.0f, 0.155f, 0.0f);
		poseStack.scale(0.5f, 0.06f, 1.15f);
		poseStack.translate(-0.5f, 0.0f, -0.5f);
		state.deckModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();

		// Trucks and wheels.
		poseStack.pushPose();
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.501f, 0.0f);
		collector.submitModel(this.model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poseStack.popPose();

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
