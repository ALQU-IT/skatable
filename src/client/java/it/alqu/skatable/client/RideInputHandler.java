package it.alqu.skatable.client;

import com.mojang.blaze3d.platform.InputConstants;
import it.alqu.skatable.Trick;
import it.alqu.skatable.entity.SkateboardEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;

/**
 * Runs once per client tick: feeds movement input to the board the local player
 * controls, detects trick key combos while airborne and applies camera follow.
 *
 * Defaults follow the movement keys (Jump + A/D = kickflip/heelflip,
 * Jump + W = pop shove-it, Jump + S = 360 spin). The dedicated key mappings in
 * the "Skatable" controls category override those when bound.
 */
public class RideInputHandler {
	private final KeyMapping kickflipKey;
	private final KeyMapping heelflipKey;
	private final KeyMapping shoveItKey;
	private final KeyMapping spinKey;
	private final KeyMapping toggleTricksKey;

	private Input lastInput = Input.EMPTY;

	public RideInputHandler(KeyMapping kickflipKey, KeyMapping heelflipKey, KeyMapping shoveItKey, KeyMapping spinKey,
			KeyMapping toggleTricksKey) {
		this.kickflipKey = kickflipKey;
		this.heelflipKey = heelflipKey;
		this.shoveItKey = shoveItKey;
		this.spinKey = spinKey;
		this.toggleTricksKey = toggleTricksKey;
	}

	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			this.lastInput = Input.EMPTY;
			return;
		}

		while (this.toggleTricksKey.consumeClick()) {
			SkatableClientConfig config = SkatableClientConfig.get();
			config.tricksEnabled = !config.tricksEnabled;
			config.save();
			TrickHudNotifier.onTricksToggled(config.tricksEnabled);
		}
		if (!(player.getVehicle() instanceof SkateboardEntity board) || board.getControllingPassenger() != player) {
			this.lastInput = Input.EMPTY;
			return;
		}

		Input input = player.input.keyPresses;
		board.setInput(input.forward(), input.backward(), input.left(), input.right(), input.jump());

		if (SkatableClientConfig.get().tricksEnabled && !board.onGround() && !board.isGrinding()) {
			Trick trick = this.pollTrick(input);
			if (trick != null && board.startTrick(trick)) {
				TrickHudNotifier.onTrickStarted(trick);
			}
		}

		if (SkatableClientConfig.get().smoothCamera) {
			double speed = board.getDeltaMovement().horizontalDistance();
			if (speed > 0.1) {
				float target = board.getYRot();
				float current = player.getYRot();
				float delta = Mth.wrapDegrees(target - current);
				player.setYRot(current + delta * SkatableClientConfig.get().cameraFollowStrength);
			}
		}

		this.lastInput = input;
	}

	/** Returns the trick whose key was freshly pressed this tick, if any. */
	private Trick pollTrick(Input input) {
		if (this.consume(this.kickflipKey)) {
			return Trick.KICKFLIP;
		}
		if (this.consume(this.heelflipKey)) {
			return Trick.HEELFLIP;
		}
		if (this.consume(this.shoveItKey)) {
			return Trick.POP_SHOVE_IT;
		}
		if (this.consume(this.spinKey)) {
			return Trick.SPIN_360;
		}
		// Fall back to the vanilla movement keys (fresh presses only).
		if (this.isUnbound(this.kickflipKey) && input.left() && !this.lastInput.left()) {
			return Trick.KICKFLIP;
		}
		if (this.isUnbound(this.heelflipKey) && input.right() && !this.lastInput.right()) {
			return Trick.HEELFLIP;
		}
		if (this.isUnbound(this.shoveItKey) && input.forward() && !this.lastInput.forward()) {
			return Trick.POP_SHOVE_IT;
		}
		if (this.isUnbound(this.spinKey) && input.backward() && !this.lastInput.backward()) {
			return Trick.SPIN_360;
		}
		return null;
	}

	private boolean consume(KeyMapping key) {
		boolean pressed = false;
		while (key.consumeClick()) {
			pressed = true;
		}
		return pressed && !this.isUnbound(key);
	}

	private boolean isUnbound(KeyMapping key) {
		return key.isUnbound();
	}
}
