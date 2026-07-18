package it.alqu.skatable;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class SkatableTags {
	public static final TagKey<Item> SKATEBOARDS = TagKey.create(Registries.ITEM, Skatable.id("skateboards"));
	/** Blocks that may never be used as a deck, even if they are full cubes. */
	public static final TagKey<Block> DECK_BLACKLIST = TagKey.create(Registries.BLOCK, Skatable.id("deck_blacklist"));
	/** Fast riding surfaces (stone-like, concrete, ...). Ice gets an extra boost in code. */
	public static final TagKey<Block> SMOOTH_SURFACES = TagKey.create(Registries.BLOCK, Skatable.id("smooth_surfaces"));
	/** Slow riding surfaces (dirt, grass, mud, ...). */
	public static final TagKey<Block> ROUGH_SURFACES = TagKey.create(Registries.BLOCK, Skatable.id("rough_surfaces"));
	/** Surfaces you cannot skate on at all (sand, soul sand, ...). */
	public static final TagKey<Block> UNRIDEABLE_SURFACES = TagKey.create(Registries.BLOCK, Skatable.id("unrideable_surfaces"));
	/** Blocks you can grind on (fences, walls, rails by default). */
	public static final TagKey<Block> GRINDABLE = TagKey.create(Registries.BLOCK, Skatable.id("grindable"));

	private SkatableTags() {
	}
}
