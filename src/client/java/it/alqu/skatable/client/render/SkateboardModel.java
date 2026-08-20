package it.alqu.skatable.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Trucks and wheels. The deck itself is rendered separately by sampling the
 * deck material's own block model, so it is not part of this model.
 * The four wheels are separate parts so they can spin with the board's speed.
 */
public class SkateboardModel extends EntityModel<SkateboardRenderState> {
	private final ModelPart[] wheels;

	public SkateboardModel(ModelPart root) {
		super(root);
		ModelPart front = root.getChild("front_truck");
		ModelPart back = root.getChild("back_truck");
		this.wheels = new ModelPart[] {
				front.getChild("left_wheel"), front.getChild("right_wheel"),
				back.getChild("left_wheel"), back.getChild("right_wheel")
		};
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Model space: 1 px = 1/16 block, y grows downward from 24 (ground at y=24).
		// Two trucks with two wheels each; the board runs along the Z axis.
		PartDefinition front = root.addOrReplaceChild("front_truck", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-3.0f, -1.0f, -0.5f, 6.0f, 1.0f, 1.0f),
				PartPose.offset(0.0f, 22.5f, 5.5f));
		PartDefinition back = root.addOrReplaceChild("back_truck", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-3.0f, -1.0f, -0.5f, 6.0f, 1.0f, 1.0f),
				PartPose.offset(0.0f, 22.5f, -5.5f));
		for (PartDefinition truck : new PartDefinition[] { front, back }) {
			truck.addOrReplaceChild("left_wheel", CubeListBuilder.create()
							.texOffs(0, 4).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
					PartPose.offset(-3.0f, 0.5f, 0.0f));
			truck.addOrReplaceChild("right_wheel", CubeListBuilder.create()
							.texOffs(8, 4).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 2.0f, 2.0f),
					PartPose.offset(3.0f, 0.5f, 0.0f));
		}

		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(SkateboardRenderState state) {
		super.setupAnim(state);
		for (ModelPart wheel : this.wheels) {
			wheel.xRot = state.wheelRoll;
		}
	}
}
