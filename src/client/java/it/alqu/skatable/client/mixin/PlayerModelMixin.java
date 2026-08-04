package it.alqu.skatable.client.mixin;

import it.alqu.skatable.Trick;
import it.alqu.skatable.client.render.SkateRideState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses the player into a skateboarding stance while riding: sideways stance,
 * bent knees, arms out for balance, leaning into carves, crouching on grinds
 * and tucking during tricks.
 *
 * <p>The stance twist is applied to the model root, because head/body/arms/legs
 * are siblings — rotating the body alone would leave the limbs facing forward.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
	private PlayerModelMixin(ModelPart root) {
		super(root);
	}

	private static final float DEG = (float) (Math.PI / 180.0);
	/** Model-space depth of the feet below the root: hips at 12 plus a 12-long leg. */
	private static final float FOOT_HEIGHT = 24.0f;
	/** Half the gap between the feet, widened from vanilla's 1.9 into a skate stance. */
	private static final float STANCE_WIDTH = 3.1f;

	/**
	 * Hides the vanilla legs so {@link it.alqu.skatable.client.render.SkateLegLayer}
	 * can draw deforming ones instead. The model instance is shared by every player
	 * the renderer draws and {@code resetPose()} only restores transforms — not
	 * visibility — so this is set on every frame, both ways.
	 */
	private void skatable$hideVanillaLegs(boolean hide) {
		PlayerModel self = (PlayerModel) (Object) this;
		this.leftLeg.visible = !hide;
		this.rightLeg.visible = !hide;
		self.leftPants.visible = !hide;
		self.rightPants.visible = !hide;
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void skatable$skatePose(AvatarRenderState state, CallbackInfo ci) {
		SkateRideState ride = state.getData(SkateRideState.KEY);
		if (ride == null) {
			// Not skating: make sure the vanilla legs are drawn normally again.
			this.skatable$hideVanillaLegs(false);
			return;
		}
		this.skatable$hideVanillaLegs(true);

		float splay = ride.goofy() ? -1.0f : 1.0f;
		float stance = 62.0f * splay;
		float lean = Mth.clamp(ride.leanDegrees() * 2.0f, -18.0f, 18.0f) * splay;
		float speed = Mth.clamp(ride.speed() / 0.7f, 0.0f, 1.0f);

		// Crouch depth: lower over the board when fast, lower still on a grind.
		float crouch = 0.3f + 0.4f * speed + (ride.grinding() ? 0.3f : 0.0f);
		crouch = Math.min(crouch, 1.0f);

		// Move the root's pivot down to the feet before rotating. A part rotates
		// about its own offset, and every candidate pivot in this model is wrong
		// for a carve: the root sits at shoulder height (leaning it throws the
		// feet off the deck) and the body sits at the neck (leaning it swings the
		// hips out and tears the torso away from the legs, which are siblings).
		// Shifting the root down and every direct child back up by the same amount
		// changes only the pivot, leaving the pose identical — so the rider now
		// leans about the board's contact patch, exactly like a real skater.
		ModelPart root = this.root();
		root.y += FOOT_HEIGHT;
		this.body.y -= FOOT_HEIGHT;
		this.head.y -= FOOT_HEIGHT;
		this.leftArm.y -= FOOT_HEIGHT;
		this.rightArm.y -= FOOT_HEIGHT;
		this.leftLeg.y -= FOOT_HEIGHT;
		this.rightLeg.y -= FOOT_HEIGHT;

		root.yRot += stance * DEG;
		root.zRot += lean * DEG;

		// The head keeps tracking where the player is actually looking.
		this.head.yRot -= stance * DEG;
		this.head.xRot = Mth.clamp(this.head.xRot, -35.0f * DEG, 35.0f * DEG);

		// Torso hunches forward over the board as speed builds.
		this.body.xRot = (8.0f + 16.0f * crouch) * DEG;

		ModelPart frontLeg = ride.goofy() ? this.rightLeg : this.leftLeg;
		ModelPart backLeg = ride.goofy() ? this.leftLeg : this.rightLeg;
		ModelPart frontArm = ride.goofy() ? this.rightArm : this.leftArm;
		ModelPart backArm = ride.goofy() ? this.leftArm : this.rightArm;

		// The curved leg mesh supplies the knee bend, so the leg parts themselves
		// stay upright — any hip swing here would compound with the curve and
		// pull the leg away from the body. They only splay apart along the board.
		frontLeg.xRot = 0.0f;
		frontLeg.yRot = 0.0f;
		frontLeg.zRot = (-5.0f * splay) * DEG;
		this.leftLeg.x = STANCE_WIDTH;

		backLeg.xRot = 0.0f;
		backLeg.yRot = 0.0f;
		backLeg.zRot = (5.0f * splay) * DEG;
		this.rightLeg.x = -STANCE_WIDTH;

		// A curved leg is shorter than a straight one, so drop the whole model by
		// exactly that difference and the feet stay planted on the deck.
		float sink = 12.0f - it.alqu.skatable.client.render.SkateLegLayer.footDrop(ride.bend(true));
		this.body.y += sink;
		this.head.y += sink;
		frontLeg.y += sink;
		backLeg.y += sink;
		frontArm.y += sink;
		backArm.y += sink;

		// Arms held out low for balance; the leading arm swings into the turn.
		frontArm.xRot = (-14.0f - 16.0f * speed) * DEG;
		frontArm.yRot = 0.0f;
		frontArm.zRot = (-42.0f * splay - lean * 0.7f) * DEG;

		backArm.xRot = (14.0f + 12.0f * speed) * DEG;
		backArm.yRot = 0.0f;
		backArm.zRot = (40.0f * splay - lean * 0.7f) * DEG;

		// Airborne: the extra knee tuck is folded into the mesh bend itself.
		if (ride.airborne()) {
			frontArm.xRot -= 18.0f * DEG;
		}

		// Tricks: tuck hard, then unfold on the way out.
		Trick trick = ride.trick();
		if (trick != null && trick != Trick.OLLIE) {
			float tuck = Mth.sin(Mth.clamp(ride.trickTime(), 0.0f, 1.0f) * Mth.PI);
			frontArm.xRot -= 35.0f * tuck * DEG;
			backArm.xRot -= 30.0f * tuck * DEG;
			this.body.xRot += 12.0f * tuck * DEG;
		}

		// Grinding wobble, so a grind reads differently from a straight roll.
		if (ride.grinding()) {
			float wobble = Mth.sin(state.ageInTicks * 0.55f) * 3.5f;
			root.zRot += wobble * DEG;
			frontArm.zRot += wobble * 1.5f * DEG;
			backArm.zRot -= wobble * 1.5f * DEG;
		}

		// The skin overlay layers (sleeves, pants, jacket, hat) are children of
		// these parts, so they follow the pose without any extra bookkeeping.
	}
}
