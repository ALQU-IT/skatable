package it.alqu.skatable.client;

import com.mojang.blaze3d.platform.InputConstants;
import it.alqu.skatable.Skatable;
import it.alqu.skatable.Trick;
import it.alqu.skatable.client.hud.TrickHud;
import it.alqu.skatable.client.mixin.SpecialModelRenderersAccessor;
import it.alqu.skatable.client.render.SkateboardModel;
import it.alqu.skatable.client.render.SkateboardRenderer;
import it.alqu.skatable.client.render.SkateboardSpecialRenderer;
import it.alqu.skatable.entity.SkateboardEntity;
import it.alqu.skatable.net.SkatableNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;

public class SkatableClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SkatableClientConfig.get();
		Skatable.clientRollSounds = () -> SkatableClientConfig.get().rollSounds;
		Skatable.clientDeckStats = () -> SkatableClientConfig.get().deckStats;

		// Rendering.
		EntityRendererRegistry.register(Skatable.SKATEBOARD_ENTITY, SkateboardRenderer::new);
		ModelLayerRegistry.registerModelLayer(SkateboardRenderer.LAYER, SkateboardModel::createBodyLayer);
		SpecialModelRenderersAccessor.skatable$getIdMapper()
				.put(Skatable.id("skateboard"), SkateboardSpecialRenderer.Unbaked.MAP_CODEC);

		// HUD.
		HudElementRegistry.addLast(Skatable.id("trick_hud"), new TrickHud());

		// Key mappings: optional overrides, unbound by default. The vanilla movement
		// keys (configurable in Controls) drive tricks when these stay unbound.
		KeyMapping.Category category = KeyMapping.Category.register(Skatable.id("skatable"));
		KeyMapping kickflip = register("kickflip", category);
		KeyMapping heelflip = register("heelflip", category);
		KeyMapping shoveIt = register("pop_shove_it", category);
		KeyMapping spin = register("spin_360", category);

		RideInputHandler inputHandler = new RideInputHandler(kickflip, heelflip, shoveIt, spin);
		ClientTickEvents.END_CLIENT_TICK.register(inputHandler::tick);

		// Networking: the common code calls into this sender on the client side,
		// and we surface HUD popups for the local rider's own tricks.
		SkatableNet.clientSender = payload -> {
			switch (payload) {
				case SkatableNet.TrickResultPayload result -> {
					Trick trick = Trick.byOrdinal(result.trickOrdinal());
					int xp = Math.round(trick.xp() * (1.0f + 0.5f * (Math.max(result.combo(), 1) - 1)));
					TrickHudNotifier.onTrickResult(trick, result.combo(), result.success(), xp);
				}
				case SkatableNet.GrindPayload grind -> {
					if (grind.grinding()) {
						TrickHudNotifier.onGrindStarted();
					}
				}
				default -> {
				}
			}
			if (ClientPlayNetworking.canSend(payload.type())) {
				ClientPlayNetworking.send(payload);
			}
		};

		ClientPlayNetworking.registerGlobalReceiver(SkatableNet.TrickAnimPayload.TYPE, (payload, context) -> {
			if (context.client().level != null
					&& context.client().level.getEntity(payload.entityId()) instanceof SkateboardEntity board) {
				board.startTrickAnimation(Trick.byOrdinal(payload.trickOrdinal()));
			}
		});
	}

	private static KeyMapping register(String name, KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.skatable." + name, InputConstants.UNKNOWN.getValue(), category));
	}
}
