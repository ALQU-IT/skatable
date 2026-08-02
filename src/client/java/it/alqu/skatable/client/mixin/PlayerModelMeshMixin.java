package it.alqu.skatable.client.mixin;

import it.alqu.skatable.client.render.SkatePartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slices each leg into a chain of stacked segments so the leg can curve — the
 * vanilla leg is one rigid 4x12x4 cuboid pivoted at the hip.
 *
 * <p>The slices reuse the leg's own skin region: a 4x12x4 box at {@code (u, v)}
 * puts its side faces at {@code v+4 .. v+16}, so a 4xHx4 box at
 * {@code (u, v + i*H)} lands exactly on the i-th strip of the leg texture. The
 * texture therefore stays continuous down the leg as it bends. These parts stay
 * hidden unless the rider is skating.
 */
@Mixin(PlayerModel.class)
public class PlayerModelMeshMixin {
	@Inject(method = "createMesh", at = @At("RETURN"))
	private static void skatable$addKnees(CubeDeformation scale, boolean slim,
			CallbackInfoReturnable<MeshDefinition> cir) {
		PartDefinition root = cir.getReturnValue().getRoot();
		// Leg skin regions: right leg (0,16), left leg (16,48).
		// Overlay (pants) regions: right (0,32), left (0,48).
		skatable$sliceLeg(root.getChild("right_leg"), scale, 0, 16, 0, 32);
		skatable$sliceLeg(root.getChild("left_leg"), scale, 16, 48, 0, 48);
	}

	private static void skatable$sliceLeg(PartDefinition leg, CubeDeformation scale,
			int legU, int legV, int overlayU, int overlayV) {
		// Grow each slice slightly so neighbours overlap and the joints do not
		// open up into visible gaps when the leg curves.
		CubeDeformation sliceScale = scale.extend(0.02f);
		CubeDeformation overlayScale = scale.extend(0.27f);
		int height = SkatePartNames.SEGMENT_HEIGHT;

		PartDefinition parent = leg;
		for (int i = 0; i < SkatePartNames.SEGMENTS; i++) {
			// The first slice sits at the hip; each later one hangs off the joint
			// above it, so rotating a slice bends everything below it.
			PartPose pose = i == 0 ? PartPose.ZERO : PartPose.offset(0.0f, height, 0.0f);
			PartDefinition segment = parent.addOrReplaceChild(SkatePartNames.segment(i),
					CubeListBuilder.create()
							.texOffs(legU, legV + i * height)
							.addBox(-2.0f, 0.0f, -2.0f, 4.0f, height, 4.0f, sliceScale),
					pose);
			segment.addOrReplaceChild(SkatePartNames.segmentOverlay(i),
					CubeListBuilder.create()
							.texOffs(overlayU, overlayV + i * height)
							.addBox(-2.0f, 0.0f, -2.0f, 4.0f, height, 4.0f, overlayScale),
					PartPose.ZERO);
			parent = segment;
		}
	}
}
