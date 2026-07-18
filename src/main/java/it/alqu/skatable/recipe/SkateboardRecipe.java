package it.alqu.skatable.recipe;

import com.mojang.serialization.MapCodec;
import it.alqu.skatable.Skatable;
import it.alqu.skatable.item.SkateboardItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Shape (in a 3x3 grid, top row empty):
 * <pre>
 *   I . I    I = iron ingot (trucks + wheels)
 *   B B B    B = three matching full blocks (the deck)
 * </pre>
 * Any full, solid, non-container block works as the deck, including modded blocks.
 */
public class SkateboardRecipe extends CustomRecipe {
	public static final MapCodec<SkateboardRecipe> MAP_CODEC = MapCodec.unit(SkateboardRecipe::new);
	public static final StreamCodec<RegistryFriendlyByteBuf, SkateboardRecipe> STREAM_CODEC =
			StreamCodec.unit(new SkateboardRecipe());

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return this.findDeck(input) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		Block deck = this.findDeck(input);
		return deck == null ? ItemStack.EMPTY : SkateboardItem.forDeck(deck);
	}

	/** Returns the deck block if the (trimmed) grid matches the skateboard shape, else null. */
	private Block findDeck(CraftingInput input) {
		if (input.width() != 3 || input.height() != 2 || input.ingredientCount() != 5) {
			return null;
		}
		// Row 0: iron, empty, iron.
		if (!input.getItem(0, 0).is(Items.IRON_INGOT)
				|| !input.getItem(1, 0).isEmpty()
				|| !input.getItem(2, 0).is(Items.IRON_INGOT)) {
			return null;
		}
		// Row 1: three matching deck blocks.
		ItemStack first = input.getItem(0, 1);
		if (!SkateboardItem.isValidDeckItem(first)) {
			return null;
		}
		for (int x = 1; x < 3; x++) {
			ItemStack stack = input.getItem(x, 1);
			if (!stack.is(first.getItem())) {
				return null;
			}
		}
		return ((BlockItem) first.getItem()).getBlock();
	}

	@Override
	public RecipeSerializer<SkateboardRecipe> getSerializer() {
		return Skatable.SKATEBOARD_RECIPE_SERIALIZER;
	}
}
