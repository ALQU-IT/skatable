package it.alqu.skatable.recipe;

import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Deck materials advertised in the recipe book. The crafting recipe itself
 * accepts (almost) any full block, which no fixed recipe can express, so the
 * book instead gets one entry per representative material — enough to teach
 * the shape and to show off what each deck family does.
 */
public final class DeckShowcase {
	public static final List<Block> DECKS = List.of(
			Blocks.OAK_PLANKS,
			Blocks.STONE,
			Blocks.CONCRETE.white(),
			Blocks.WOOL.white(),
			Blocks.PACKED_ICE,
			Blocks.GLASS,
			Blocks.TERRACOTTA,
			Blocks.IRON_BLOCK,
			Blocks.GOLD_BLOCK,
			Blocks.COPPER_BLOCK.weathering().pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED),
			Blocks.NETHERRACK,
			Blocks.OBSIDIAN,
			Blocks.PRISMARINE,
			Blocks.SCULK,
			Blocks.PURPUR_BLOCK,
			Blocks.DIAMOND_BLOCK,
			Blocks.NETHERITE_BLOCK,
			Blocks.EMERALD_BLOCK,
			Blocks.REDSTONE_BLOCK,
			Blocks.SLIME_BLOCK,
			Blocks.HONEY_BLOCK,
			Blocks.TNT,
			Blocks.MAGMA_BLOCK,
			Blocks.AMETHYST_BLOCK);

	private DeckShowcase() {
	}
}
