package it.alqu.skatable.client.mixin;

import it.alqu.skatable.Trick;
import it.alqu.skatable.client.render.SkateRideState;
import it.alqu.skatable.entity.SkateboardEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skateboarders stand on the deck instead of using the vanilla seated riding
 * pose (isPassenger is what makes HumanoidModel bend the legs), and carry the
 * board's state over to the model so it can be posed for skating.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL")
	)
	private void skatable$standOnSkateboard(Avatar player, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (!(player.getVehicle() instanceof SkateboardEntity board)) {
			// Render states are reused between frames, so a stale ride would keep
			// posing the player long after they stepped off the board.
			state.setData(SkateRideState.KEY, null);
			return;
		}
		state.isPassenger = false;

		// Carving rate: how fast the board's heading is changing this tick.
		float lean = Mth.wrapDegrees(board.getYRot() - board.yRotO);
		float speed = (float) board.getDeltaMovement().horizontalDistance();

		Trick trick = null;
		float trickTime = 0.0f;
		if (board.animTicks > 0 && board.animTrick != null && board.animDuration > 0) {
			trick = board.animTrick;
			trickTime = Mth.clamp(1.0f - (board.animTicks - partialTicks) / board.animDuration, 0.0f, 1.0f);
		}

		// Stable per-player stance, so a given skater is always regular or goofy.
		boolean goofy = (player.getUUID().hashCode() & 1) == 1;

		state.setData(SkateRideState.KEY, new SkateRideState(
				lean, speed, board.isGrinding(), !board.onGround(), trick, trickTime, goofy));
	}
}
