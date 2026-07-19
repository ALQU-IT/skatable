package it.alqu.skatable.mixin;

import it.alqu.skatable.entity.SkateboardEntity;
import it.alqu.skatable.power.DeckPower;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Riding a glass-deck board makes you harder for mobs to spot. */
@Mixin(LivingEntity.class)
public class GlassStealthMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void skatable$glassStealth(Entity lookingEntity, CallbackInfoReturnable<Double> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getVehicle() instanceof SkateboardEntity board && board.power() == DeckPower.GLASS) {
			cir.setReturnValue(cir.getReturnValue() * 0.4);
		}
	}
}
