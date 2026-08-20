package it.alqu.skatable.net;

import it.alqu.skatable.Skatable;
import it.alqu.skatable.Trick;
import it.alqu.skatable.entity.SkateboardEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Client to server: the riding player reports trick starts/results, grinds and crashes
 * (board physics are client-authoritative for the controlling rider, like boats).
 * Server to client: trick animations are mirrored to spectating players.
 */
public final class SkatableNet {
	public record TrickStartPayload(int trickOrdinal) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<TrickStartPayload> TYPE = new CustomPacketPayload.Type<>(Skatable.id("trick_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TrickStartPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, TrickStartPayload::trickOrdinal, TrickStartPayload::new);

		@Override
		public CustomPacketPayload.Type<TrickStartPayload> type() {
			return TYPE;
		}
	}

	public record TrickResultPayload(int trickOrdinal, int combo, boolean success) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<TrickResultPayload> TYPE = new CustomPacketPayload.Type<>(Skatable.id("trick_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TrickResultPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, TrickResultPayload::trickOrdinal,
				ByteBufCodecs.VAR_INT, TrickResultPayload::combo,
				ByteBufCodecs.BOOL, TrickResultPayload::success,
				TrickResultPayload::new);

		@Override
		public CustomPacketPayload.Type<TrickResultPayload> type() {
			return TYPE;
		}
	}

	public record GrindPayload(boolean grinding) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<GrindPayload> TYPE = new CustomPacketPayload.Type<>(Skatable.id("grind"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GrindPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.BOOL, GrindPayload::grinding, GrindPayload::new);

		@Override
		public CustomPacketPayload.Type<GrindPayload> type() {
			return TYPE;
		}
	}

	public record CrashPayload(float speed) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<CrashPayload> TYPE = new CustomPacketPayload.Type<>(Skatable.id("crash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CrashPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.FLOAT, CrashPayload::speed, CrashPayload::new);

		@Override
		public CustomPacketPayload.Type<CrashPayload> type() {
			return TYPE;
		}
	}

	/** R key pressed while riding: run the board's active power. */
	public record ActivatePowerPayload(byte marker) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ActivatePowerPayload> TYPE = new CustomPacketPayload.Type<>(Skatable.id("activate_power"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActivatePowerPayload> CODEC =
				StreamCodec.composite(ByteBufCodecs.BYTE, ActivatePowerPayload::marker, ActivatePowerPayload::new);

		public ActivatePowerPayload() {
			this((byte) 0);
		}

		@Override
		public CustomPacketPayload.Type<ActivatePowerPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client on join: which deck powers the admin has disabled. */
	public record DisabledPowersPayload(java.util.List<String> ids) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DisabledPowersPayload> TYPE = new CustomPacketPayload.Type<>(Skatable.id("disabled_powers"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DisabledPowersPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DisabledPowersPayload::ids,
				DisabledPowersPayload::new);

		@Override
		public CustomPacketPayload.Type<DisabledPowersPayload> type() {
			return TYPE;
		}
	}

	public record TrickAnimPayload(int entityId, int trickOrdinal) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<TrickAnimPayload> TYPE = new CustomPacketPayload.Type<>(Skatable.id("trick_anim"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TrickAnimPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, TrickAnimPayload::entityId,
				ByteBufCodecs.VAR_INT, TrickAnimPayload::trickOrdinal,
				TrickAnimPayload::new);

		@Override
		public CustomPacketPayload.Type<TrickAnimPayload> type() {
			return TYPE;
		}
	}

	public static void registerCommon() {
		PayloadTypeRegistry.serverboundPlay().register(TrickStartPayload.TYPE, TrickStartPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TrickResultPayload.TYPE, TrickResultPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GrindPayload.TYPE, GrindPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CrashPayload.TYPE, CrashPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActivatePowerPayload.TYPE, ActivatePowerPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TrickAnimPayload.TYPE, TrickAnimPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DisabledPowersPayload.TYPE, DisabledPowersPayload.CODEC);
	}

	public static void registerServerHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(TrickStartPayload.TYPE, (payload, context) -> {
			SkateboardEntity board = ridingBoard(context.player());
			if (board == null) {
				return;
			}
			Trick trick = Trick.byOrdinal(payload.trickOrdinal());
			// Mirror the animation to everyone else tracking the board.
			TrickAnimPayload anim = new TrickAnimPayload(board.getId(), payload.trickOrdinal());
			for (ServerPlayer other : PlayerLookup.tracking(board)) {
				if (other != context.player()) {
					ServerPlayNetworking.send(other, anim);
				}
			}
			board.level().playSound(null, board.getX(), board.getY(), board.getZ(),
					Skatable.SOUND_TRICK, board.getSoundSource(), 0.5f, 1.1f);
		});

		ServerPlayNetworking.registerGlobalReceiver(TrickResultPayload.TYPE, (payload, context) -> {
			SkateboardEntity board = ridingBoard(context.player());
			if (board == null) {
				return;
			}
			Trick trick = Trick.byOrdinal(payload.trickOrdinal());
			int combo = Mth.clamp(payload.combo(), 1, 20);
			if (payload.success()) {
				var power = board.power();
				int xp = Math.round(trick.xp() * (1.0f + 0.5f * (combo - 1)));
				if (trick == Trick.GRIND && power == it.alqu.skatable.power.DeckPower.TERRACOTTA) {
					xp *= 2;
				}
				if (power == it.alqu.skatable.power.DeckPower.DIAMOND) {
					xp = Math.round(xp * 1.25f);
				}
				context.player().giveExperiencePoints(xp);
				var serverLevel = (net.minecraft.server.level.ServerLevel) board.level();
				serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
						board.getX(), board.getY() + 0.3, board.getZ(), 8, 0.3, 0.2, 0.3, 0.15);
				switch (power) {
					case GOLD -> net.minecraft.world.entity.ExperienceOrb.award(serverLevel, board.position(),
							1 + serverLevel.getRandom().nextInt(2));
					case EMERALD -> {
						var villagers = serverLevel.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
								board.getBoundingBox().inflate(8.0));
						if (!villagers.isEmpty()) {
							for (var villager : villagers) {
								serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
										villager.getX(), villager.getY() + 1.8, villager.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
							}
							context.player().addEffect(new net.minecraft.world.effect.MobEffectInstance(
									net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE, 600, 0, true, false));
						}
					}
					case AMETHYST -> {
						board.level().playSound(null, board.getX(), board.getY(), board.getZ(),
								net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, board.getSoundSource(),
								1.0f, 0.7f + 0.15f * Math.min(combo, 6));
						board.addResonance(25);
					}
					default -> {
					}
				}
			} else {
				// Bail: the client already dismounted locally; make it authoritative
				// server-side too, otherwise the player can never remount.
				board.ejectPassengers();
				board.damageBoard(1);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(GrindPayload.TYPE, (payload, context) -> {
			SkateboardEntity board = ridingBoard(context.player());
			if (board != null) {
				board.setGrindingFromNetwork(payload.grinding());
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(ActivatePowerPayload.TYPE, (payload, context) -> {
			SkateboardEntity board = ridingBoard(context.player());
			if (board != null) {
				board.tryActivatePower(context.player());
			}
		});

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				sender.sendPacket(new DisabledPowersPayload(
						java.util.List.copyOf(it.alqu.skatable.power.DeckPowers.disabledIds()))));

		ServerPlayNetworking.registerGlobalReceiver(CrashPayload.TYPE, (payload, context) -> {
			SkateboardEntity board = ridingBoard(context.player());
			if (board == null) {
				// The client dismounts before the packet arrives, so also accept a board very close by.
				board = nearbyBoard(context.player());
			}
			if (board == null) {
				return;
			}
			float speed = Mth.clamp(payload.speed(), 0.0f, 2.0f);
			if (speed >= SkateboardEntity.CRASH_SPEED * 0.9f) {
				board.level().playSound(null, board.getX(), board.getY(), board.getZ(),
						Skatable.SOUND_CRASH, board.getSoundSource(), 1.0f, 1.0f);
				board.ejectPassengers();
				board.damageBoard(1 + (int) (speed * 6.0f));
				context.player().hurt(context.player().damageSources().flyIntoWall(), Math.min(speed * 4.0f, 4.0f));
			}
		});
	}

	private static SkateboardEntity ridingBoard(ServerPlayer player) {
		return player.getVehicle() instanceof SkateboardEntity board ? board : null;
	}

	private static SkateboardEntity nearbyBoard(ServerPlayer player) {
		var list = player.level().getEntitiesOfClass(SkateboardEntity.class, player.getBoundingBox().inflate(3.0));
		return list.isEmpty() ? null : list.getFirst();
	}

	// -------- client senders (safe to call only on the client; implemented via ClientPlayNetworking reflection-free holder)

	public static ClientSender clientSender = payload -> {
	};

	public interface ClientSender {
		void send(CustomPacketPayload payload);
	}

	public static void sendTrickStart(Trick trick) {
		clientSender.send(new TrickStartPayload(trick.ordinal()));
	}

	public static void sendTrickResult(Trick trick, int combo, boolean success) {
		clientSender.send(new TrickResultPayload(trick.ordinal(), Math.max(combo, 1), success));
	}

	public static void sendGrind(boolean grinding) {
		clientSender.send(new GrindPayload(grinding));
	}

	public static void sendCrash(float speed) {
		clientSender.send(new CrashPayload(speed));
	}

	public static void sendActivatePower() {
		clientSender.send(new ActivatePowerPayload());
	}

	private SkatableNet() {
	}
}
