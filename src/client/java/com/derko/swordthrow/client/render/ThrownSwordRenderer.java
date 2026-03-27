package com.derko.swordthrow.client.render;

import com.derko.seamlessapi.api.visual.SeamlessVec3;
import com.derko.seamlessapi.api.visual.SpinTumbleAnimator;
import com.derko.seamlessapi.api.visual.ThrownItemVisualProfile;
import com.derko.seamlessapi.api.visual.TrailMath;
import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import com.derko.swordthrow.SwordThrowMod;
import java.util.ArrayList;
import com.derko.swordthrow.entity.ThrownSwordEntity;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class ThrownSwordRenderer extends EntityRenderer<ThrownSwordEntity, ThrownSwordRenderer.ThrownSwordRenderState> {
    private static final Identifier TRAIL_TEXTURE = SwordThrowMod.id("textures/effect/sword_trail.png");
    private static final RenderLayer TRAIL_LAYER = RenderLayers.entityTranslucentEmissive(TRAIL_TEXTURE);
    private static final float OUTER_TRAIL_WIDTH = 0.15F;
    private static final float INNER_TRAIL_WIDTH = OUTER_TRAIL_WIDTH / 1.5F;
    private static final int TRAIL_SUBDIVISIONS = 4;
    private static final ThrownItemVisualProfile VISUAL_PROFILE = ThrownItemVisualProfile.swordthrowDefaults();
    private static final SpinTumbleAnimator SPIN_ANIMATOR = SpinTumbleAnimator.swordthrowDefaults();
    private static final Map<Integer, Integer> LAST_TWINKLE_AGE = new HashMap<>();
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

        boolean trailEnabled = SwordThrowClientConfig.get().trailEffectEnabled();
        if (trailEnabled && !state.embedded && state.trailPoints.size() > 1) {
            renderTrail(state, matrices, queue, cameraState, OUTER_TRAIL_WIDTH, 0.95F);
            renderTrail(state, matrices, queue, cameraState, INNER_TRAIL_WIDTH, 0.63F);
            spawnTrailTwinkle(state);
        }

        if (state.pointFirstFlight) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.flightYaw + 90.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-state.flightPitch + 90.0F + VISUAL_PROFILE.pointFirstPitchBias()));
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.flightYaw + 90.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-state.flightPitch + 90.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.roll));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.tumbleYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.tumblePitch));
        }
        matrices.scale(state.renderScale, state.renderScale, state.renderScale);

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
        state.embedded = entity.isEmbedded();
        state.pointFirstFlight = entity.usesPointFirstFlight();
        state.renderScale = getThrownRenderScale(entity.getStack());
        state.trailPoints = state.embedded ? Collections.emptyList() : entity.getTrailPoints();

        Vec3d velocity = entity.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        state.flightYaw = horizontalSpeed > 1.0E-5D ? (float)Math.toDegrees(Math.atan2(velocity.x, velocity.z)) : entity.getYaw();
        state.flightPitch = velocity.lengthSquared() > 1.0E-5D ? (float)Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed)) : entity.getPitch();
        state.entityId = entity.getId();
        state.entityAge = entity.age;
        state.velocitySquared = velocity.lengthSquared();

        if (state.embedded) {
            state.roll = entity.getEmbeddedRoll();
            state.tumbleYaw = 0.0F;
            state.tumblePitch = 0.0F;
            SPIN_ANIMATOR.clear(state.entityId);
            return;
        }

        if (state.pointFirstFlight) {
            state.roll = 0.0F;
            state.tumbleYaw = 0.0F;
            state.tumblePitch = 0.0F;
            SPIN_ANIMATOR.clear(state.entityId);
            return;
        }

        SpinTumbleAnimator.Orientation orientation = SPIN_ANIMATOR.sample(
            state.entityId,
            state.entityAge,
            fromMinecraftVec(velocity),
            tickDelta
        );
        state.roll = orientation.roll();
        state.tumbleYaw = orientation.tumbleYaw();
        state.tumblePitch = orientation.tumblePitch();
    }

    private static float getThrownRenderScale(ItemStack stack) {
        return VISUAL_PROFILE.resolveScale(isSmallThrownItem(stack));
    }

    private static boolean isSmallThrownItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        UseAction useAction = stack.getUseAction();
        if (useAction == UseAction.EAT || useAction == UseAction.DRINK) {
            return true;
        }

        String itemPath = Registries.ITEM.getId(stack.getItem()).getPath();
        return VISUAL_PROFILE.isSmallItemPath(itemPath);
    }

    private static void spawnTrailTwinkle(ThrownSwordRenderState state) {
        if (state.velocitySquared < 0.01D) {
            return;
        }

        Integer lastAge = LAST_TWINKLE_AGE.get(state.entityId);
        if (lastAge != null && lastAge == state.entityAge) {
            return;
        }
        LAST_TWINKLE_AGE.put(state.entityId, state.entityAge);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.world.random.nextInt(100) >= 5) {
            return;
        }

        double spread = 0.08D;
        client.world.addParticleClient(
            ParticleTypes.GLOW,
            state.x + client.world.random.nextGaussian() * spread,
            state.y - 0.2D + client.world.random.nextGaussian() * spread,
            state.z + client.world.random.nextGaussian() * spread,
            0.0D,
            0.02D,
            0.0D
        );
    }

    private static void renderTrail(
        ThrownSwordRenderState state,
        MatrixStack matrices,
        OrderedRenderCommandQueue queue,
        CameraRenderState cameraState,
        float width,
        float alphaScale
    ) {
        int trailColor = SwordThrowClientConfig.get().trailColor();
        queue.submitCustom(matrices, TRAIL_LAYER, (entry, consumer) -> {
            List<Vec3d> points = getSmoothedTrailPoints(state);
            if (points.size() < 2) {
                return;
            }

            Vec3d currentPos = points.get(points.size() - 1);
            for (int index = 0; index < points.size() - 1; index++) {
                Vec3d point = points.get(index);
                Vec3d nextPoint = points.get(index + 1);

                float progress0 = index / (float)(points.size() - 1);
                float progress1 = (index + 1) / (float)(points.size() - 1);
                emitTrailSegment(entry, consumer, cameraState, currentPos, point, nextPoint, width, alphaScale, progress0, progress1, trailColor);
            }
        });
    }

    private static List<Vec3d> getSmoothedTrailPoints(ThrownSwordRenderState state) {
        if (state.trailPoints.size() < 2) {
            return state.trailPoints;
        }

        List<SeamlessVec3> sourcePoints = new ArrayList<>(state.trailPoints.size());
        for (Vec3d point : state.trailPoints) {
            sourcePoints.add(fromMinecraftVec(point));
        }

        List<SeamlessVec3> smoothed = TrailMath.smoothCatmullRom(
            sourcePoints,
            TRAIL_SUBDIVISIONS,
            new SeamlessVec3(state.x, state.y, state.z)
        );

        List<Vec3d> converted = new ArrayList<>(smoothed.size());
        for (SeamlessVec3 point : smoothed) {
            converted.add(toMinecraftVec(point));
        }
        return converted;
    }

    private static void emitTrailSegment(
        MatrixStack.Entry entry,
        VertexConsumer consumer,
        CameraRenderState cameraState,
        Vec3d origin,
        Vec3d worldStart,
        Vec3d worldEnd,
        float width,
        float alphaScale,
        float progress0,
        float progress1,
        int trailColor
    ) {
        Vec3d startToEnd = worldEnd.subtract(worldStart);
        if (startToEnd.lengthSquared() < 1.0E-5D) {
            return;
        }

        Vec3d midpoint = worldStart.add(worldEnd).multiply(0.5D);
        Vec3d cameraToMid = cameraState.pos.subtract(midpoint);
        Vec3d side = startToEnd.crossProduct(cameraToMid);

        if (side.lengthSquared() < 1.0E-5D) {
            side = startToEnd.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        }

        if (side.lengthSquared() < 1.0E-5D) {
            return;
        }

        side = side.normalize();
        Vec3d sideStart = side.multiply(TrailMath.widthAtProgress(width, progress0));
        Vec3d sideEnd = side.multiply(TrailMath.widthAtProgress(width, progress1));

        Vec3d start = worldStart.subtract(origin);
        Vec3d end = worldEnd.subtract(origin);
        Vec3d startLeft = start.add(sideStart);
        Vec3d startRight = start.subtract(sideStart);
        Vec3d endLeft = end.add(sideEnd);
        Vec3d endRight = end.subtract(sideEnd);

        int alphaStart = Math.max(0, Math.round(255.0F * TrailMath.alphaAtProgress(alphaScale, progress0)));
        int alphaEnd = Math.max(0, Math.round(255.0F * TrailMath.alphaAtProgress(alphaScale, progress1)));
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        putVertex(consumer, entry, startLeft, trailColor, alphaStart, 0.0F, progress0, light);
        putVertex(consumer, entry, endLeft, trailColor, alphaEnd, 0.0F, progress1, light);
        putVertex(consumer, entry, endRight, trailColor, alphaEnd, 1.0F, progress1, light);
        putVertex(consumer, entry, startRight, trailColor, alphaStart, 1.0F, progress0, light);
    }

    private static SeamlessVec3 fromMinecraftVec(Vec3d vec) {
        return new SeamlessVec3(vec.x, vec.y, vec.z);
    }

    private static Vec3d toMinecraftVec(SeamlessVec3 vec) {
        return new Vec3d(vec.x(), vec.y(), vec.z());
    }

    private static void putVertex(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d position, int color, int alpha, float u, float v, int light) {
        consumer.vertex(entry, (float)position.x, (float)position.y, (float)position.z)
            .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(entry, 0.0F, 1.0F, 0.0F);
    }

    public static class ThrownSwordRenderState extends EntityRenderState {
        public final ItemRenderState itemRenderState = new ItemRenderState();
        public boolean embedded;
        public boolean pointFirstFlight;
        public int entityId;
        public int entityAge;
        public float flightYaw;
        public float flightPitch;
        public float roll;
        public float renderScale = VISUAL_PROFILE.thrownItemScale();
        public float tumbleYaw;
        public float tumblePitch;
        public double velocitySquared;
        public List<Vec3d> trailPoints = Collections.emptyList();
    }
}