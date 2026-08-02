package it.alqu.skatable.client.mixin;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import it.alqu.skatable.client.render.SkatePartNames;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Splits each leg into a thigh and a shin so the player can actually bend a
 * knee — the vanilla leg is one rigid 4x12x4 cuboid pivoted at the hip.
 *
 * <p>The halves reuse the leg's own skin region: a 4x12x4 box at {@code (u, v)}
 * puts its side faces at {@code v+4 .. v+16}, so a 4x6x4 box at {@code (u, v)}
 * covers the upper half and one at {@code (u, v+6)} covers the lower half.
 * These parts stay hidden unless the rider is skating.
 */
@Mixin(PlayerModel.class)
public class PlayerModelMeshMixin {
	@Inject(method = "createMesh", at = @At("RETURN"))
	private static void skatable$addKnees(CubeDeformation scale, boolean slim,
			CallbackInfoReturnable<MeshDefinition> cir) {
		PartDefinition root = cir.getReturnValue().getRoot();
		// Leg skin regions: right leg (0,16), left leg (16,48).
		// Overlay (pants) regions: right (0,32), left (0,48).
		skatable$splitLeg(root.getChild("right_leg"), scale, 0, 16, 0, 32);
		skatable$splitLeg(root.getChild("left_leg"), scale, 16, 48, 0, 48);
	}

	private static void skatable$splitLeg(PartDefinition leg, CubeDeformation scale,
			int legU, int legV, int overlayU, int overlayV) {
		CubeDeformation overlayScale = scale.extend(0.25f);

		PartDefinition thigh = leg.addOrReplaceChild(SkatePartNames.THIGH,
				CubeListBuilder.create().texOffs(legU, legV).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f, scale),
				PartPose.ZERO);
		thigh.addOrReplaceChild(SkatePartNames.THIGH_OVERLAY,
				CubeListBuilder.create().texOffs(overlayU, overlayV).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f, overlayScale),
				PartPose.ZERO);

		// The shin hangs off the knee, so rotating it bends the leg there.
		PartDefinition shin = thigh.addOrReplaceChild(SkatePartNames.SHIN,
				CubeListBuilder.create().texOffs(legU, legV + 6).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f, scale),
				PartPose.offset(0.0f, 6.0f, 0.0f));
		shin.addOrReplaceChild(SkatePartNames.SHIN_OVERLAY,
				CubeListBuilder.create().texOffs(overlayU, overlayV + 6).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 6.0f, 4.0f, overlayScale),
				PartPose.ZERO);
	}
}
