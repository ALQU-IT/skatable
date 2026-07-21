package it.alqu.skatable;

import it.alqu.skatable.entity.SkateboardEntity;
import it.alqu.skatable.item.SkateboardItem;
import it.alqu.skatable.net.SkatableNet;
import it.alqu.skatable.recipe.SkateboardRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Skatable implements ModInitializer {
	public static final String MOD_ID = "skatable";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * Client-config hooks. Board physics and ambience run on the riding player's
	 * client, so these honor the local config there; on a dedicated server they
	 * keep their defaults.
	 */
	public static java.util.function.BooleanSupplier clientRollSounds = () -> true;
	public static java.util.function.BooleanSupplier clientDeckStats = () -> true;
	public static java.util.function.BooleanSupplier clientTricksEnabled = () -> true;

	// Deck material, stored on the item stack and mirrored into the entity's synched board stack.
	public static final DataComponentType<Block> DECK_COMPONENT = DataComponentType.<Block>builder()
			.persistent(BuiltInRegistries.BLOCK.byNameCodec())
			.networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.registry(Registries.BLOCK))
			.build();

	// Persistent deck-power state (cooldown, copper oxidation, amethyst resonance).
	public static final DataComponentType<it.alqu.skatable.power.PowerData> POWER_DATA_COMPONENT =
			DataComponentType.<it.alqu.skatable.power.PowerData>builder()
					.persistent(it.alqu.skatable.power.PowerData.CODEC)
					.networkSynchronized(it.alqu.skatable.power.PowerData.STREAM_CODEC)
					.build();

	public static final ResourceKey<Item> SKATEBOARD_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("skateboard"));
	public static final SkateboardItem SKATEBOARD_ITEM = new SkateboardItem(new Item.Properties()
			.setId(SKATEBOARD_ITEM_KEY)
			.stacksTo(1)
			.durability(SkateboardItem.statsFor(Blocks.OAK_PLANKS).maxDamage())
			.enchantable(12)
			.component(DECK_COMPONENT, Blocks.OAK_PLANKS));

	public static final ResourceKey<EntityType<?>> SKATEBOARD_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("skateboard"));
	public static final EntityType<SkateboardEntity> SKATEBOARD_ENTITY = EntityType.Builder
			.<SkateboardEntity>of(SkateboardEntity::new, MobCategory.MISC)
			.sized(0.9f, 0.2f)
			.clientTrackingRange(10)
			.build(SKATEBOARD_ENTITY_KEY);

	public static final RecipeSerializer<SkateboardRecipe> SKATEBOARD_RECIPE_SERIALIZER =
			new RecipeSerializer<>(SkateboardRecipe.MAP_CODEC, SkateboardRecipe.STREAM_CODEC);

	public static final RecipeSerializer<it.alqu.skatable.recipe.FluidBoardRecipe> FLUID_BOARD_RECIPE_SERIALIZER =
			new RecipeSerializer<>(it.alqu.skatable.recipe.FluidBoardRecipe.MAP_CODEC, it.alqu.skatable.recipe.FluidBoardRecipe.STREAM_CODEC);

	public static final ResourceKey<Enchantment> GRIP_TAPE = ResourceKey.create(Registries.ENCHANTMENT, id("grip_tape"));
	public static final ResourceKey<Enchantment> SWIFT_BEARINGS = ResourceKey.create(Registries.ENCHANTMENT, id("swift_bearings"));

	public static final SoundEvent SOUND_OLLIE = sound("skateboard.ollie");
	public static final SoundEvent SOUND_LAND = sound("skateboard.land");
	public static final SoundEvent SOUND_GRIND = sound("skateboard.grind");
	public static final SoundEvent SOUND_CRASH = sound("skateboard.crash");
	public static final SoundEvent SOUND_BREAK = sound("skateboard.break");
	public static final SoundEvent SOUND_TRICK = sound("skateboard.trick");

	private static SoundEvent sound(String path) {
		return SoundEvent.createVariableRangeEvent(id(path));
	}

	@Override
	public void onInitialize() {
		SkatableCommonConfig.get();
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("deck"), DECK_COMPONENT);
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("power_data"), POWER_DATA_COMPONENT);
		Registry.register(BuiltInRegistries.ITEM, SKATEBOARD_ITEM_KEY, SKATEBOARD_ITEM);
		Registry.register(BuiltInRegistries.ENTITY_TYPE, SKATEBOARD_ENTITY_KEY, SKATEBOARD_ENTITY);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("crafting_skateboard"), SKATEBOARD_RECIPE_SERIALIZER);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("crafting_fluid_board"), FLUID_BOARD_RECIPE_SERIALIZER);
		for (SoundEvent event : new SoundEvent[] { SOUND_OLLIE, SOUND_LAND, SOUND_GRIND, SOUND_CRASH, SOUND_BREAK, SOUND_TRICK }) {
			Registry.register(BuiltInRegistries.SOUND_EVENT, event.location(), event);
		}

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(SkateboardItem.forDeck(Blocks.OAK_PLANKS), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(SkateboardItem.forDeck(Blocks.STONE), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(SkateboardItem.forDeck(Blocks.DIAMOND_BLOCK), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(SkateboardItem.forDeck(Blocks.WATER), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
			output.accept(SkateboardItem.forDeck(Blocks.LAVA), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		});

		SkatableNet.registerCommon();
		SkatableNet.registerServerHandlers();

		LOGGER.info("Skatable rolling in!");
	}

	public static ItemStack defaultBoard() {
		return SkateboardItem.forDeck(Blocks.OAK_PLANKS);
	}
}
