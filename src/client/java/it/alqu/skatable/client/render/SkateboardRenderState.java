package it.alqu.skatable.client.render;

import it.alqu.skatable.Trick;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class SkateboardRenderState extends EntityRenderState {
	public final BlockModelRenderState deckModel = new BlockModelRenderState();
	public float yRot;
	public float hurtTime;
	public int hurtDir;
	public float damageTime;
	public Trick trick;
	/** 0..1 over the trick animation. */
	public float trickProgress;
	public boolean grinding;
}
