package it.alqu.skatable.client.mixin;

import it.alqu.skatable.entity.SkateboardEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skateboarders stand on the deck instead of using the vanilla seated riding
 * pose (isPassenger is what makes HumanoidModel bend the legs).
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL")
	)
	private void skatable$standOnSkateboard(Avatar player, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (player.getVehicle() instanceof SkateboardEntity) {
			state.isPassenger = false;
		}
	}
}
