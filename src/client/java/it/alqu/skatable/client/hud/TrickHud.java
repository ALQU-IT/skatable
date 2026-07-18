package it.alqu.skatable.client.hud;

import it.alqu.skatable.client.SkatableClientConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Shows "Kickflip!" style popups and XP rewards while skating. */
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
