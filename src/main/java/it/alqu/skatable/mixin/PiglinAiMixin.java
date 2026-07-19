package it.alqu.skatable.mixin;

import it.alqu.skatable.entity.SkateboardEntity;
import it.alqu.skatable.power.DeckPower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A gold-deck skateboard counts as wearing gold for piglins. */
@Mixin(PiglinAi.class)
public class PiglinAiMixin {
	@Inject(method = "isWearingSafeArmor", at = @At("HEAD"), cancellable = true)
	private static void skatable$goldBoardIsSafe(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity.getVehicle() instanceof SkateboardEntity board && board.power() == DeckPower.GOLD) {
			cir.setReturnValue(true);
		}
	}
}
