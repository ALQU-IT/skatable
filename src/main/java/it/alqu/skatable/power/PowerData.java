package it.alqu.skatable.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Persistent power state carried by the board item (and mirrored into the
 * entity while placed): active-ability cooldown, copper ride time/oxidation,
 * and the amethyst resonance meter.
 */
public record PowerData(int cooldown, int rideTime, int oxidation, int resonance) {
	public static final PowerData EMPTY = new PowerData(0, 0, 0, 0);

	public static final Codec<PowerData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("cooldown", 0).forGetter(PowerData::cooldown),
			Codec.INT.optionalFieldOf("ride_time", 0).forGetter(PowerData::rideTime),
			Codec.INT.optionalFieldOf("oxidation", 0).forGetter(PowerData::oxidation),
			Codec.INT.optionalFieldOf("resonance", 0).forGetter(PowerData::resonance)
	).apply(i, PowerData::new));

	public static final StreamCodec<io.netty.buffer.ByteBuf, PowerData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PowerData::cooldown,
			ByteBufCodecs.VAR_INT, PowerData::rideTime,
			ByteBufCodecs.VAR_INT, PowerData::oxidation,
			ByteBufCodecs.VAR_INT, PowerData::resonance,
			PowerData::new);

	public boolean isEmpty() {
		return this.equals(EMPTY);
	}
}
