package com.derko.swordthrow.client.render;

import com.derko.swordthrow.SwordThrowMod;
import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import com.derko.swordthrow.client.render.visual.SeamlessVec3;
import com.derko.swordthrow.client.render.visual.SpinTumbleAnimator;
import com.derko.swordthrow.client.render.visual.ThrownItemVisualProfile;
import com.derko.swordthrow.client.render.visual.TrailMath;
import com.derko.swordthrow.entity.ThrownSwordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class ThrownSwordRenderer extends EntityRenderer<ThrownSwordEntity, ThrownSwordRenderer.ThrownSwordRenderState> {
    private static final Identifier TRAIL_TEXTURE = SwordThrowMod.id("textures/effect/sword_trail.png");
    private static final RenderType TRAIL_LAYER = RenderTypes.entityTranslucentEmissive(TRAIL_TEXTURE);
    private static final float OUTER_TRAIL_WIDTH = 0.15F;
    private static final float INNER_TRAIL_WIDTH = OUTER_TRAIL_WIDTH / 1.5F;
    private static final int TRAIL_SUBDIVISIONS = 4;

    private static final ThrownItemVisualProfile VISUAL_PROFILE = ThrownItemVisualProfile.swordthrowDefaults();
    private static final SpinTumbleAnimator SPIN_ANIMATOR = SpinTumbleAnimator.swordthrowDefaults();
    private static final Map<Integer, Integer> LAST_TWINKLE_AGE = new HashMap<>();

    private final ItemModelResolver itemModelResolver;

    public ThrownSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public ThrownSwordRenderState createRenderState() {
        return new ThrownSwordRenderState();
    }

    @Override
    public void extractRenderState(ThrownSwordEntity entity, ThrownSwordRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.itemStack = entity.getItem().copy();
        this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.FIXED, entity);
        state.deltaMovement = entity.getDeltaMovement();
        state.currentPos = entity.getPosition(partialTick);
        state.embedded = entity.isEmbedded();
        state.embeddedRoll = entity.getEmbeddedRoll();
        state.renderYaw = state.embedded
            ? entity.getEmbeddedYaw()
            : Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.renderPitch = state.embedded
            ? entity.getEmbeddedPitch()
            : Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        state.pointFirstFlight = entity.usesPointFirstFlight();
        state.entityId = entity.getId();
        state.entityAge = entity.tickCount;
        state.trailEnabled = SwordThrowClientConfig.get().trailEffectEnabled();
        state.trailColor = SwordThrowClientConfig.get().trailColor();
        state.trailPoints.clear();
        state.trailPoints.addAll(entity.getTrailPoints());
    }

    @Override
    public void submit(ThrownSwordRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        if (state.trailEnabled && !state.embedded && state.trailPoints.size() > 1) {
            submitTrail(state, poseStack, collector, camera, OUTER_TRAIL_WIDTH, 0.95F);
            submitTrail(state, poseStack, collector, camera, INNER_TRAIL_WIDTH, 0.63F);
            spawnTrailTwinkle(state);
        }

        Vec3 velocity = state.deltaMovement;
        boolean useStoredRotation = state.embedded || velocity.lengthSqr() < 1.0E-6D;
        float flightYaw = useStoredRotation
            ? state.renderYaw
            : (float)Math.toDegrees(Math.atan2(velocity.x, velocity.z));
        float horizontalSpeed = (float)Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float flightPitch = useStoredRotation
            ? state.renderPitch
            : (float)Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed));

        poseStack.mulPose(Axis.YP.rotationDegrees(flightYaw + 90.0F));

        if (state.embedded) {
            float embeddedPitchBias = state.pointFirstFlight ? VISUAL_PROFILE.pointFirstPitchBias() : 0.0F;
            poseStack.mulPose(Axis.ZP.rotationDegrees(-flightPitch + 90.0F + embeddedPitchBias));
        } else if (state.pointFirstFlight) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-flightPitch + 90.0F + VISUAL_PROFILE.pointFirstPitchBias()));
        } else {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-flightPitch + 90.0F));

            SpinTumbleAnimator.Orientation orientation = SPIN_ANIMATOR.sample(
                state.entityId,
                state.entityAge,
                new SeamlessVec3(velocity.x, velocity.y, velocity.z),
                state.partialTick
            );
            poseStack.mulPose(Axis.ZP.rotationDegrees(orientation.roll()));
            poseStack.mulPose(Axis.YP.rotationDegrees(orientation.tumbleYaw()));
            poseStack.mulPose(Axis.XP.rotationDegrees(orientation.tumblePitch()));
        }

        if (state.embedded) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.embeddedRoll));
        }

        float scale = VISUAL_PROFILE.resolveScale(isSmallThrownItem(state.itemStack()));
        poseStack.scale(scale, scale, scale);

        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);

        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void spawnTrailTwinkle(ThrownSwordRenderState state) {
        if (state.deltaMovement.lengthSqr() < 0.01D) {
            return;
        }

        Integer lastAge = LAST_TWINKLE_AGE.get(state.entityId);
        if (lastAge != null && lastAge == state.entityAge) {
            return;
        }
        LAST_TWINKLE_AGE.put(state.entityId, state.entityAge);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.random.nextInt(100) >= 5) {
            return;
        }

        double spread = 0.08D;
        minecraft.level.addParticle(
            ParticleTypes.GLOW,
            state.currentPos.x + minecraft.level.random.nextGaussian() * spread,
            state.currentPos.y - 0.2D + minecraft.level.random.nextGaussian() * spread,
            state.currentPos.z + minecraft.level.random.nextGaussian() * spread,
            0.0D,
            0.02D,
            0.0D
        );
    }

    private void submitTrail(
        ThrownSwordRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState camera,
        float width,
        float alphaScale
    ) {
        List<Vec3> smoothed = getSmoothedTrailPoints(state);
        if (smoothed.size() < 2) {
            return;
        }

        Vec3 cameraPos = camera.pos;
        Vec3 currentPos = state.currentPos;
        int color = state.trailColor;
        collector.submitCustomGeometry(poseStack, TRAIL_LAYER, (pose, consumer) -> {
            for (int index = 0; index < smoothed.size() - 1; index++) {
                Vec3 start = smoothed.get(index);
                Vec3 end = smoothed.get(index + 1);
                float progress0 = index / (float)(smoothed.size() - 1);
                float progress1 = (index + 1) / (float)(smoothed.size() - 1);
                emitTrailSegment(pose, consumer, cameraPos, currentPos, start, end, width, alphaScale, progress0, progress1, color);
            }
        });
    }

    private static List<Vec3> getSmoothedTrailPoints(ThrownSwordRenderState state) {
        List<Vec3> trail = state.trailPoints;
        if (trail.size() < 2) {
            return trail;
        }

        List<SeamlessVec3> sourcePoints = new ArrayList<>(trail.size());
        for (Vec3 point : trail) {
            sourcePoints.add(new SeamlessVec3(point.x, point.y, point.z));
        }

        Vec3 currentPos = state.currentPos;
        List<SeamlessVec3> smoothed = TrailMath.smoothCatmullRom(
            sourcePoints,
            TRAIL_SUBDIVISIONS,
            new SeamlessVec3(currentPos.x, currentPos.y, currentPos.z)
        );

        List<Vec3> converted = new ArrayList<>(smoothed.size());
        for (SeamlessVec3 point : smoothed) {
            converted.add(new Vec3(point.x(), point.y(), point.z()));
        }
        return converted;
    }

    private static void emitTrailSegment(
        Pose pose,
        VertexConsumer consumer,
        Vec3 cameraPos,
        Vec3 origin,
        Vec3 worldStart,
        Vec3 worldEnd,
        float width,
        float alphaScale,
        float progress0,
        float progress1,
        int trailColor
    ) {
        Vec3 startToEnd = worldEnd.subtract(worldStart);
        if (startToEnd.lengthSqr() < 1.0E-5D) {
            return;
        }

        Vec3 midpoint = worldStart.add(worldEnd).scale(0.5D);
        Vec3 cameraToMid = cameraPos.subtract(midpoint);
        Vec3 side = startToEnd.cross(cameraToMid);

        if (side.lengthSqr() < 1.0E-5D) {
            side = startToEnd.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() < 1.0E-5D) {
            return;
        }

        side = side.normalize();
        Vec3 sideStart = side.scale(TrailMath.widthAtProgress(width, progress0));
        Vec3 sideEnd = side.scale(TrailMath.widthAtProgress(width, progress1));

        Vec3 start = worldStart.subtract(origin);
        Vec3 end = worldEnd.subtract(origin);
        Vec3 startLeft = start.add(sideStart);
        Vec3 startRight = start.subtract(sideStart);
        Vec3 endLeft = end.add(sideEnd);
        Vec3 endRight = end.subtract(sideEnd);

        int alphaStart = Math.max(0, Math.round(255.0F * TrailMath.alphaAtProgress(alphaScale, progress0)));
        int alphaEnd = Math.max(0, Math.round(255.0F * TrailMath.alphaAtProgress(alphaScale, progress1)));

        putVertex(consumer, pose, startLeft, trailColor, alphaStart, 0.0F, progress0);
        putVertex(consumer, pose, endLeft, trailColor, alphaEnd, 0.0F, progress1);
        putVertex(consumer, pose, endRight, trailColor, alphaEnd, 1.0F, progress1);
        putVertex(consumer, pose, startRight, trailColor, alphaStart, 1.0F, progress0);
    }

    private static void putVertex(VertexConsumer consumer, Pose pose, Vec3 position, int color, int alpha, float u, float v) {
        consumer.addVertex(pose.pose(), (float)position.x, (float)position.y, (float)position.z)
            .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static boolean isSmallThrownItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ItemUseAnimation useAnim = stack.getUseAnimation();
        if (useAnim == ItemUseAnimation.EAT || useAnim == ItemUseAnimation.DRINK) {
            return true;
        }

        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return VISUAL_PROFILE.isSmallItemPath(itemPath);
    }

    public static final class ThrownSwordRenderState extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public final List<Vec3> trailPoints = new ArrayList<>();
        public ItemStack itemStack = ItemStack.EMPTY;
        public Vec3 currentPos = Vec3.ZERO;
        public Vec3 deltaMovement = Vec3.ZERO;
        public boolean embedded;
        public boolean pointFirstFlight;
        public float embeddedRoll;
        public float renderYaw;
        public float renderPitch;
        public int entityId;
        public int entityAge;
        public boolean trailEnabled;
        public int trailColor;

        public ItemStack itemStack() {
            return this.itemStack;
        }
    }
}
