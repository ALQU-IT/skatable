package it.alqu.skatable.power;

import it.alqu.skatable.Skatable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Maps deck blocks to powers. Signature blocks win; otherwise the first
 * matching {@code skatable:power/<family>} block tag decides, so datapacks can
 * reassign families or add modded blocks without code changes.
 */
public final class DeckPowers {
	private static final Map<Block, DeckPower> SIGNATURE = new LinkedHashMap<>();
	private static final List<Map.Entry<TagKey<Block>, DeckPower>> FAMILIES;
	/** Powers switched off by the server admin (synced to clients on join). */
	private static volatile Set<String> disabled = Set.of();

	static {
		SIGNATURE.put(Blocks.DIAMOND_BLOCK, DeckPower.DIAMOND);
		SIGNATURE.put(Blocks.NETHERITE_BLOCK, DeckPower.NETHERITE);
		SIGNATURE.put(Blocks.EMERALD_BLOCK, DeckPower.EMERALD);
		SIGNATURE.put(Blocks.REDSTONE_BLOCK, DeckPower.REDSTONE);
		SIGNATURE.put(Blocks.SLIME_BLOCK, DeckPower.SLIME);
		SIGNATURE.put(Blocks.HONEY_BLOCK, DeckPower.HONEY);
		SIGNATURE.put(Blocks.TNT, DeckPower.TNT);
		SIGNATURE.put(Blocks.MAGMA_BLOCK, DeckPower.MAGMA);
		SIGNATURE.put(Blocks.AMETHYST_BLOCK, DeckPower.AMETHYST);
		SIGNATURE.put(Blocks.DRAGON_EGG, DeckPower.DRAGON);

		FAMILIES = List.of(
				family("stone", DeckPower.STONE),
				family("concrete", DeckPower.CONCRETE),
				family("wool", DeckPower.WOOL),
				family("ice", DeckPower.ICE),
				family("glass", DeckPower.GLASS),
				family("terracotta", DeckPower.TERRACOTTA),
				family("iron", DeckPower.IRON),
				family("gold", DeckPower.GOLD),
				family("copper", DeckPower.COPPER),
				family("nether", DeckPower.NETHER),
				family("obsidian", DeckPower.OBSIDIAN),
				family("prismarine", DeckPower.PRISMARINE),
				family("sculk", DeckPower.SCULK),
				family("end", DeckPower.END));
	}

	private static Map.Entry<TagKey<Block>, DeckPower> family(String name, DeckPower power) {
		return Map.entry(TagKey.create(Registries.BLOCK, Skatable.id("power/" + name)), power);
	}

	/** Resolves the power for a deck block, honoring the disabled list. */
	public static DeckPower resolve(Block block) {
		DeckPower power = resolveRaw(block);
		return isDisabled(power) ? DeckPower.NONE : power;
	}

	private static DeckPower resolveRaw(Block block) {
		DeckPower signature = SIGNATURE.get(block);
		if (signature != null) {
			return signature;
		}
		var state = block.defaultBlockState();
		try {
			for (var entry : FAMILIES) {
				if (state.is(entry.getKey())) {
					return entry.getValue();
				}
			}
		} catch (IllegalStateException e) {
			// Tags are not bound yet (registration time); families resolve as NONE
			// until the first datapack load.
			return DeckPower.NONE;
		}
		return DeckPower.NONE;
	}

	public static boolean isDisabled(DeckPower power) {
		return power != DeckPower.NONE && disabled.contains(power.id());
	}

	public static void setDisabled(Iterable<String> ids) {
		Set<String> set = new HashSet<>();
		for (String id : ids) {
			set.add(id.toLowerCase(java.util.Locale.ROOT));
		}
		disabled = Set.copyOf(set);
	}

	public static Set<String> disabledIds() {
		return disabled;
	}

	/**
	 * Visual-only copper oxidation stages for the plain copper-block deck,
	 * resolved by id (the copper block constants became collections in 26.x).
	 */
	private static final String[] COPPER_STAGE_IDS = {
			"copper_block", "exposed_copper", "weathered_copper", "oxidized_copper"
	};

	public static Block oxidizedVisual(Block deck, int stage) {
		if (stage <= 0 || deck != copperStage(0)) {
			return deck;
		}
		return copperStage(Math.min(stage, COPPER_STAGE_IDS.length - 1));
	}

	private static Block copperStage(int stage) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(COPPER_STAGE_IDS[stage]));
	}

	private DeckPowers() {
	}
}
