package com.derko.swordthrow.client.render;

import com.derko.swordthrow.entity.ThrownSwordEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class ThrownSwordRenderer extends EntityRenderer<ThrownSwordEntity, ThrownSwordRenderer.ThrownSwordRenderState> {
    private final ItemModelManager itemModelManager;

    public ThrownSwordRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemModelManager = context.getItemModelManager();
    }

    @Override
    public void render(ThrownSwordRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        if (state.itemRenderState.isEmpty()) {
            super.render(state, matrices, queue, cameraState);
            return;
        }

        matrices.push();

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.flightYaw + 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-state.flightPitch + 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.spin));
        matrices.scale(1.2F, 1.2F, 1.2F);

        state.itemRenderState.render(matrices, queue, state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);

        matrices.pop();
        super.render(state, matrices, queue, cameraState);
    }

    @Override
    public ThrownSwordRenderState createRenderState() {
        return new ThrownSwordRenderState();
    }

    @Override
    public void updateRenderState(ThrownSwordEntity entity, ThrownSwordRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        this.itemModelManager.updateForNonLivingEntity(state.itemRenderState, entity.getStack(), ItemDisplayContext.FIXED, entity);

        Vec3d velocity = entity.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        state.flightYaw = horizontalSpeed > 1.0E-5D ? (float)Math.toDegrees(Math.atan2(velocity.x, velocity.z)) : entity.getYaw();
        state.flightPitch = velocity.lengthSquared() > 1.0E-5D ? (float)Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed)) : entity.getPitch();
        state.spin = (entity.age + tickDelta) * 32.0F;
    }

    public static class ThrownSwordRenderState extends EntityRenderState {
        public final ItemRenderState itemRenderState = new ItemRenderState();
        public float flightYaw;
        public float flightPitch;
        public float spin;
    }
}