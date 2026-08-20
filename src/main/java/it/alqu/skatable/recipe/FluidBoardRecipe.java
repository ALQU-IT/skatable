package it.alqu.skatable.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.alqu.skatable.Skatable;
import it.alqu.skatable.item.SkateboardItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Upgrades any existing skateboard into a fluid board by combining it with a
 * full bucket (shapeless): skateboard + water/lava bucket -> water/lava board.
 * The emptied bucket is left in the crafting grid, so it is retrievable.
 */
public class FluidBoardRecipe extends CustomRecipe {
	public static final MapCodec<FluidBoardRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BuiltInRegistries.ITEM.byNameCodec().fieldOf("bucket").forGetter(r -> r.bucket),
			BuiltInRegistries.BLOCK.byNameCodec().fieldOf("deck").forGetter(r -> r.deck)
	).apply(i, FluidBoardRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, FluidBoardRecipe> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.registry(Registries.ITEM), r -> r.bucket,
			ByteBufCodecs.registry(Registries.BLOCK), r -> r.deck,
			FluidBoardRecipe::new);

	private final Item bucket;
	private final Block deck;

	public FluidBoardRecipe(Item bucket, Block deck) {
		this.bucket = bucket;
		this.deck = deck;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		boolean hasBoard = false;
		boolean hasBucket = false;
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(Skatable.SKATEBOARD_ITEM) && !hasBoard) {
				hasBoard = true;
			} else if (stack.is(this.bucket) && !hasBucket) {
				hasBucket = true;
			} else {
				return false;
			}
		}
		return hasBoard && hasBucket;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return SkateboardItem.forDeck(this.deck);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		for (int slot = 0; slot < input.size(); slot++) {
			if (input.getItem(slot).is(this.bucket)) {
				remaining.set(slot, new ItemStack(Items.BUCKET));
			}
		}
		return remaining;
	}

	/** Recipe-book entry: an oak board plus the bucket, yielding the fluid board. */
	@Override
	public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
		var board = new net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(
				net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(
						SkateboardItem.forDeck(net.minecraft.world.level.block.Blocks.OAK_PLANKS)));
		var bucketSlot = new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(this.bucket);
		return java.util.List.of(new net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay(
				java.util.List.of(board, bucketSlot),
				SkateboardRecipe.resultDisplay(this.deck),
				new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
	}

	@Override
	public RecipeSerializer<? extends CustomRecipe> getSerializer() {
		return Skatable.FLUID_BOARD_RECIPE_SERIALIZER;
	}
}
