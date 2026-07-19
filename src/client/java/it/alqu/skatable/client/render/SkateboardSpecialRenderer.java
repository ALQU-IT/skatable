package it.alqu.skatable.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import it.alqu.skatable.item.SkateboardItem;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Item model renderer: draws the skateboard with the deck sampled from the
 * deck material's block model, so any block (including modded ones) shows its
 * real texture in the inventory and in hand.
 */
public class SkateboardSpecialRenderer implements SpecialModelRenderer<Block> {
	private static final Identifier TEXTURE = it.alqu.skatable.Skatable.id("textures/entity/skateboard.png");
	private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

	private final ModelPart trucks;
	private final BlockModelResolver blockModelResolver;
	private final BlockModelRenderState deckModel = new BlockModelRenderState();

	public SkateboardSpecialRenderer(ModelPart trucks) {
		this.trucks = trucks;
		this.blockModelResolver = new BlockModelResolver(Minecraft.getInstance().getModelManager());
	}

	@Override
	public Block extractArgument(ItemStack stack) {
		return it.alqu.skatable.power.DeckPowers.oxidizedVisual(
				SkateboardItem.deckOf(stack), SkateboardItem.powerData(stack).oxidation());
	}

	@Override
	public void submit(Block deck, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
			boolean hasFoil, int outlineColor) {
		if (deck == null) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.0f, 0.5f);

		this.blockModelResolver.update(this.deckModel, deck.defaultBlockState(), BLOCK_DISPLAY_CONTEXT);
		poseStack.pushPose();
		poseStack.translate(0.0f, 0.155f, 0.0f);
		poseStack.scale(0.5f, 0.06f, 1.15f);
		poseStack.translate(-0.5f, 0.0f, -0.5f);
		this.deckModel.submit(poseStack, collector, lightCoords, overlayCoords, outlineColor);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.501f, 0.0f);
		collector.submitModelPart(this.trucks, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, overlayCoords, null);
		poseStack.popPose();

		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(0.25f, 0.0f, -0.075f));
		output.accept(new Vector3f(0.25f, 1.0f, -0.075f));
		output.accept(new Vector3f(0.25f, 0.0f, 1.075f));
		output.accept(new Vector3f(0.25f, 1.0f, 1.075f));
		output.accept(new Vector3f(0.75f, 0.0f, -0.075f));
		output.accept(new Vector3f(0.75f, 1.0f, -0.075f));
		output.accept(new Vector3f(0.75f, 0.0f, 1.075f));
		output.accept(new Vector3f(0.75f, 1.0f, 1.075f));
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<Block> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<Block> bake(SpecialModelRenderer.BakingContext context) {
			return new SkateboardSpecialRenderer(context.entityModelSet().bakeLayer(SkateboardRenderer.LAYER));
		}
	}
}
