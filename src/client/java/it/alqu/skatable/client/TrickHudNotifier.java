package it.alqu.skatable.client;

import it.alqu.skatable.Trick;
import it.alqu.skatable.client.hud.TrickHud;
import net.minecraft.client.resources.language.I18n;

/** Formats trick events into HUD popups. */
public final class TrickHudNotifier {
	public static void onTrickStarted(Trick trick) {
		TrickHud.show(I18n.get(trick.translationKey()) + "!", "", 1200);
	}

	public static void onGrindStarted() {
		TrickHud.show(I18n.get(Trick.GRIND.translationKey()) + "!", "", 1200);
	}

	public static void onPowerOnCooldown(int ticks) {
		TrickHud.show(I18n.get("hud.skatable.power_cooldown", (ticks + 19) / 20), "", 900);
	}

	public static void onTricksToggled(boolean enabled) {
		TrickHud.show(I18n.get(enabled ? "hud.skatable.tricks_on" : "hud.skatable.tricks_off"), "", 1500);
	}

	public static void onTrickResult(Trick trick, int combo, boolean success, int xp) {
		if (success) {
			String sub = combo > 1
					? I18n.get("hud.skatable.combo", combo, xp)
					: I18n.get("hud.skatable.landed", xp);
			TrickHud.show(I18n.get(trick.translationKey()) + "!", sub, 1600);
		} else {
			TrickHud.show(I18n.get("hud.skatable.bailed"), "", 1200);
		}
	}

	private TrickHudNotifier() {
	}
}
