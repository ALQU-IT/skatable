package it.alqu.skatable.client.hud;

import it.alqu.skatable.client.SkatableClientConfig;
import it.alqu.skatable.entity.SkateboardEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

/** Shows "Kickflip!" style popups, XP rewards, and the deck power cooldown. */
public class TrickHud implements HudElement {
	private static String title = "";
	private static String subtitle = "";
	private static long showUntilMillis;
	private static long showStartMillis;

	public static void show(String newTitle, String newSubtitle, int durationMillis) {
		title = newTitle;
		subtitle = newSubtitle;
		showStartMillis = System.currentTimeMillis();
		showUntilMillis = showStartMillis + durationMillis;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!SkatableClientConfig.get().showHud) {
			return;
		}
		this.renderPowerIndicator(graphics);
		this.renderTrickPopup(graphics);
	}

	/** Small bar above the hotbar: active-power readiness / cooldown. */
	private void renderPowerIndicator(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null
				|| !(minecraft.player.getVehicle() instanceof SkateboardEntity board)) {
			return;
		}
		var power = board.power();
		if (!power.isActive()) {
			return;
		}
		Font font = minecraft.font;
		int centerX = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() - 50;
		int width = 60;
		int cooldown = board.powerCooldown();
		if (cooldown <= 0) {
			String label = I18n.get("hud.skatable.power_ready", I18n.get(power.nameKey()));
			graphics.text(font, label, centerX - font.width(label) / 2, y - 10, 0xFF7CFC00, true);
			graphics.fill(centerX - width / 2, y, centerX + width / 2, y + 3, 0xFF55DD33);
		} else {
			float progress = 1.0f - cooldown / (float) power.cooldownTicks();
			int filled = (int) (width * Mth.clamp(progress, 0.0f, 1.0f));
			String label = String.valueOf((cooldown + 19) / 20);
			graphics.text(font, label, centerX - font.width(label) / 2, y - 10, 0xFFCCCCCC, true);
			graphics.fill(centerX - width / 2, y, centerX + width / 2, y + 3, 0x88222222);
			graphics.fill(centerX - width / 2, y, centerX - width / 2 + filled, y + 3, 0xFFAAAAAA);
		}
	}

	private void renderTrickPopup(GuiGraphicsExtractor graphics) {
		long now = System.currentTimeMillis();
		if (now >= showUntilMillis || title.isEmpty()) {
			return;
		}
		float remaining = (showUntilMillis - now) / (float) (showUntilMillis - showStartMillis);
		int alpha = (int) (Mth.clamp(remaining * 3.0f, 0.0f, 1.0f) * 255.0f);
		if (alpha < 8) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		int centerX = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 4;
		int titleColor = (alpha << 24) | 0xFFF060;
		int subColor = (alpha << 24) | 0xFFFFFF;
		graphics.text(font, title, centerX - font.width(title) / 2, y, titleColor, true);
		if (!subtitle.isEmpty()) {
			graphics.text(font, subtitle, centerX - font.width(subtitle) / 2, y + 12, subColor, true);
		}
	}
}
