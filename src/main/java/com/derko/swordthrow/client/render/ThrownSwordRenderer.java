package com.derko.swordthrow.client.render;

import com.derko.seamlessapi.api.visual.SeamlessVec3;
import com.derko.seamlessapi.api.visual.SpinTumbleAnimator;
import com.derko.seamlessapi.api.visual.ThrownItemVisualProfile;
import com.derko.seamlessapi.api.visual.TrailMath;
import com.derko.swordthrow.SwordThrowMod;
import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import com.derko.swordthrow.entity.ThrownSwordEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;

public class ThrownSwordRenderer extends EntityRenderer<ThrownSwordEntity> {
    private static final ResourceLocation TRAIL_TEXTURE = SwordThrowMod.id("textures/effect/sword_trail.png");
    private static final RenderType TRAIL_LAYER = RenderType.entityTranslucentEmissive(TRAIL_TEXTURE);
    private static final float OUTER_TRAIL_WIDTH = 0.15F;
    private static final float INNER_TRAIL_WIDTH = OUTER_TRAIL_WIDTH / 1.5F;
    private static final int TRAIL_SUBDIVISIONS = 4;

    private static final ThrownItemVisualProfile VISUAL_PROFILE = ThrownItemVisualProfile.swordthrowDefaults();
    private static final SpinTumbleAnimator SPIN_ANIMATOR = SpinTumbleAnimator.swordthrowDefaults();
    private static final Map<Integer, Integer> LAST_TWINKLE_AGE = new HashMap<>();

    private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

    public ThrownSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownSwordEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        if (SwordThrowClientConfig.get().trailEffectEnabled() && !entity.isEmbedded() && entity.getTrailPoints().size() > 1) {
            renderTrail(entity, partialTick, poseStack, buffer, OUTER_TRAIL_WIDTH, 0.95F);
            renderTrail(entity, partialTick, poseStack, buffer, INNER_TRAIL_WIDTH, 0.63F);
            spawnTrailTwinkle(entity);
        }

        Vec3 velocity = entity.getDeltaMovement();
        float flightYaw = (float)Math.toDegrees(Math.atan2(velocity.x, velocity.z));
        float horizontalSpeed = (float)Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float flightPitch = (float)Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed));

        poseStack.mulPose(Axis.YP.rotationDegrees(flightYaw + 90.0F));

        if (entity.usesPointFirstFlight()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-flightPitch + 90.0F + VISUAL_PROFILE.pointFirstPitchBias()));
        } else {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-flightPitch + 90.0F));

            SpinTumbleAnimator.Orientation orientation = SPIN_ANIMATOR.sample(
                entity.getId(),
                entity.tickCount,
                new SeamlessVec3(velocity.x, velocity.y, velocity.z),
                partialTick
            );
            poseStack.mulPose(Axis.ZP.rotationDegrees(orientation.roll()));
            poseStack.mulPose(Axis.YP.rotationDegrees(orientation.tumbleYaw()));
            poseStack.mulPose(Axis.XP.rotationDegrees(orientation.tumblePitch()));
        }

        if (entity.isEmbedded()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getEmbeddedRoll()));
        }

        float scale = VISUAL_PROFILE.resolveScale(isSmallThrownItem(stack));
        poseStack.scale(scale, scale, scale);

        this.itemRenderer.renderStatic(
            stack,
            ItemDisplayContext.FIXED,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            entity.level(),
            entity.getId()
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownSwordEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static void spawnTrailTwinkle(ThrownSwordEntity entity) {
        if (entity.getDeltaMovement().lengthSqr() < 0.01D) {
            return;
        }

        Integer lastAge = LAST_TWINKLE_AGE.get(entity.getId());
        if (lastAge != null && lastAge == entity.tickCount) {
            return;
        }
        LAST_TWINKLE_AGE.put(entity.getId(), entity.tickCount);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.random.nextInt(100) >= 5) {
            return;
        }

        double spread = 0.08D;
        minecraft.level.addParticle(
            ParticleTypes.GLOW,
            entity.getX() + minecraft.level.random.nextGaussian() * spread,
            entity.getY() - 0.2D + minecraft.level.random.nextGaussian() * spread,
            entity.getZ() + minecraft.level.random.nextGaussian() * spread,
            0.0D,
            0.02D,
            0.0D
        );
    }

    private void renderTrail(
        ThrownSwordEntity entity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffer,
        float width,
        float alphaScale
    ) {
        List<Vec3> smoothed = getSmoothedTrailPoints(entity, partialTick);
        if (smoothed.size() < 2) {
            return;
        }

        VertexConsumer consumer = buffer.getBuffer(TRAIL_LAYER);
        int color = SwordThrowClientConfig.get().trailColor();
        Vec3 currentPos = entity.getPosition(partialTick);

        Vec3 cameraPos = this.entityRenderDispatcher.camera.getPosition();
        for (int index = 0; index < smoothed.size() - 1; index++) {
            Vec3 start = smoothed.get(index);
            Vec3 end = smoothed.get(index + 1);
            float progress0 = index / (float)(smoothed.size() - 1);
            float progress1 = (index + 1) / (float)(smoothed.size() - 1);
            emitTrailSegment(poseStack, consumer, cameraPos, currentPos, start, end, width, alphaScale, progress0, progress1, color);
        }
    }

    private static List<Vec3> getSmoothedTrailPoints(ThrownSwordEntity entity, float partialTick) {
        List<Vec3> trail = entity.getTrailPoints();
        if (trail.size() < 2) {
            return trail;
        }

        List<SeamlessVec3> sourcePoints = new ArrayList<>(trail.size());
        for (Vec3 point : trail) {
            sourcePoints.add(new SeamlessVec3(point.x, point.y, point.z));
        }

        Vec3 currentPos = entity.getPosition(partialTick);
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
        PoseStack poseStack,
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

        putVertex(consumer, poseStack, startLeft, trailColor, alphaStart, 0.0F, progress0);
        putVertex(consumer, poseStack, endLeft, trailColor, alphaEnd, 0.0F, progress1);
        putVertex(consumer, poseStack, endRight, trailColor, alphaEnd, 1.0F, progress1);
        putVertex(consumer, poseStack, startRight, trailColor, alphaStart, 1.0F, progress0);
    }

    private static void putVertex(VertexConsumer consumer, PoseStack poseStack, Vec3 position, int color, int alpha, float u, float v) {
        consumer.addVertex(poseStack.last().pose(), (float)position.x, (float)position.y, (float)position.z)
            .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(poseStack.last(), 0.0F, 1.0F, 0.0F);
    }

    private static boolean isSmallThrownItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        UseAnim useAnim = stack.getUseAnimation();
        if (useAnim == UseAnim.EAT || useAnim == UseAnim.DRINK) {
            return true;
        }

        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return VISUAL_PROFILE.isSmallItemPath(itemPath);
    }
}
