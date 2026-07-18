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
 */
public class SkateboardModel extends EntityModel<SkateboardRenderState> {
	public SkateboardModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Model space: 1 px = 1/16 block, y grows downward from 24 (ground at y=24).
		// Two trucks with two wheels each; the board runs along the Z axis.
		PartDefinition front = root.addOrReplaceChild("front_truck", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-3.0f, -1.0f, -0.5f, 6.0f, 1.0f, 1.0f)   // axle
						.texOffs(0, 4).addBox(-4.0f, -0.5f, -1.0f, 2.0f, 2.0f, 2.0f)   // left wheel
						.texOffs(8, 4).addBox(2.0f, -0.5f, -1.0f, 2.0f, 2.0f, 2.0f),   // right wheel
				PartPose.offset(0.0f, 22.5f, 5.5f));
		PartDefinition back = root.addOrReplaceChild("back_truck", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-3.0f, -1.0f, -0.5f, 6.0f, 1.0f, 1.0f)
						.texOffs(0, 4).addBox(-4.0f, -0.5f, -1.0f, 2.0f, 2.0f, 2.0f)
						.texOffs(8, 4).addBox(2.0f, -0.5f, -1.0f, 2.0f, 2.0f, 2.0f),
				PartPose.offset(0.0f, 22.5f, -5.5f));

		return LayerDefinition.create(mesh, 32, 32);
	}
}
