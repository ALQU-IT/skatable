package it.alqu.skatable.item;

import it.alqu.skatable.Skatable;
import it.alqu.skatable.SkatableTags;
import it.alqu.skatable.entity.SkateboardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SkateboardItem extends Item {
	public SkateboardItem(Properties properties) {
		super(properties);
	}

	/** Small per-material stat spread: heavy decks are tougher but accelerate slower. */
	public record DeckStats(int maxDamage, float accelMultiplier) {
	}

	public static DeckStats statsFor(Block block) {
		float hardness = Mth.clamp(block.defaultDestroyTime(), 0.4f, 6.0f);
		int maxDamage = 90 + Math.round(hardness * 45.0f);
		float accel = Mth.clamp(1.12f - 0.04f * hardness, 0.88f, 1.12f);
		return new DeckStats(maxDamage, accel);
	}

	public static boolean isValidDeckBlock(Block block) {
		BlockState state = block.defaultBlockState();
		if (block instanceof EntityBlock || block instanceof FallingBlock) {
			return false;
		}
		if (state.is(SkatableTags.DECK_BLACKLIST)) {
			return false;
		}
		return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	public static boolean isValidDeckItem(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && isValidDeckBlock(blockItem.getBlock());
	}

	public static Block deckOf(ItemStack stack) {
		Block block = stack.get(Skatable.DECK_COMPONENT);
		return block != null ? block : Blocks.OAK_PLANKS;
	}

	/** Creates a skateboard stack for the given deck material, applying material stats. */
	public static ItemStack forDeck(Block block) {
		ItemStack stack = new ItemStack(Skatable.SKATEBOARD_ITEM);
		applyDeck(stack, block);
		return stack;
	}

	public static void applyDeck(ItemStack stack, Block block) {
		DeckStats stats = statsFor(block);
		stack.set(Skatable.DECK_COMPONENT, block);
		stack.set(DataComponents.MAX_DAMAGE, stats.maxDamage());
		stack.set(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(block.asItem().builtInRegistryHolder())));
	}

	@Override
	public Component getName(ItemStack stack) {
		Block deck = deckOf(stack);
		return Component.translatable("item.skatable.skateboard.named", deck.getName());
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		BlockPos placePos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
				? clicked
				: clicked.relative(context.getClickedFace());
		if (!level.getBlockState(placePos).getCollisionShape(level, placePos).isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = context.getItemInHand();
		SkateboardEntity board = Skatable.SKATEBOARD_ENTITY.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (board == null) {
			return InteractionResult.FAIL;
		}
		Vec3 pos = Vec3.atBottomCenterOf(placePos);
		board.absSnapTo(pos.x, pos.y, pos.z, context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0f, 0.0f);
		board.setBoardItem(stack.copyWithCount(1));
		level.addFreshEntity(board);
		level.playSound(null, board.getX(), board.getY(), board.getZ(), Skatable.SOUND_LAND, board.getSoundSource(), 0.8f, 1.0f);
		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}
}
