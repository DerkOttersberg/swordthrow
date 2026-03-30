package com.derko.swordthrow.client.render;

import com.derko.seamlessapi.api.visual.SeamlessVec3;
import com.derko.seamlessapi.api.visual.SpinTumbleAnimator;
import com.derko.seamlessapi.api.visual.ThrownItemVisualProfile;
import com.derko.seamlessapi.api.visual.TrailMath;
import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import com.derko.swordthrow.SwordThrowMod;
import com.derko.swordthrow.entity.ThrownSwordEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ThrownSwordRenderer extends EntityRenderer<ThrownSwordEntity> {
    private static final Identifier TRAIL_TEXTURE = SwordThrowMod.id("textures/effect/sword_trail.png");
    private static final RenderLayer TRAIL_LAYER = RenderLayer.getEntityTranslucent(TRAIL_TEXTURE);
    private static final float OUTER_TRAIL_WIDTH = 0.15F;
    private static final float INNER_TRAIL_WIDTH = OUTER_TRAIL_WIDTH / 1.5F;
    private static final int TRAIL_SUBDIVISIONS = 4;
    private static final ThrownItemVisualProfile VISUAL_PROFILE = ThrownItemVisualProfile.swordthrowDefaults();
    private static final SpinTumbleAnimator SPIN_ANIMATOR = SpinTumbleAnimator.swordthrowDefaults();
    private static final Map<Integer, Integer> LAST_TWINKLE_AGE = new HashMap<>();
    private final ItemRenderer itemRenderer;

    public ThrownSwordRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownSwordEntity entity, float entityYaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        ItemStack stack = entity.getStack();
        if (stack.isEmpty()) {
            super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }

        Vec3d currentPosition = getInterpolatedPosition(entity, tickDelta);
        boolean embedded = entity.isEmbedded();
        boolean pointFirstFlight = entity.usesPointFirstFlight();

        matrices.push();

        boolean trailEnabled = SwordThrowClientConfig.get().trailEffectEnabled();
        List<Vec3d> trailPoints = embedded ? Collections.emptyList() : entity.getTrailPoints();
        if (trailEnabled && trailPoints.size() > 1) {
            Vec3d cameraPosition = this.dispatcher.camera.getPos();
            renderTrail(matrices, vertexConsumers, currentPosition, cameraPosition, trailPoints, OUTER_TRAIL_WIDTH, 0.95F);
            renderTrail(matrices, vertexConsumers, currentPosition, cameraPosition, trailPoints, INNER_TRAIL_WIDTH, 0.63F);
            spawnTrailTwinkle(entity, currentPosition);
        }

        Vec3d velocity = entity.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float flightYaw = horizontalSpeed > 1.0E-5D ? (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z)) : entity.getYaw();
        float flightPitch = velocity.lengthSquared() > 1.0E-5D ? (float) Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed)) : entity.getPitch();

        float roll = 0.0F;
        float tumbleYaw = 0.0F;
        float tumblePitch = 0.0F;

        if (embedded) {
            roll = entity.getEmbeddedRoll();
            SPIN_ANIMATOR.clear(entity.getId());
        } else if (!pointFirstFlight) {
            SpinTumbleAnimator.Orientation orientation = SPIN_ANIMATOR.sample(
                entity.getId(),
                entity.age,
                fromMinecraftVec(velocity),
                tickDelta
            );
            roll = orientation.roll();
            tumbleYaw = orientation.tumbleYaw();
            tumblePitch = orientation.tumblePitch();
        } else {
            SPIN_ANIMATOR.clear(entity.getId());
        }

        if (pointFirstFlight) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(flightYaw + 90.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-flightPitch + 90.0F + VISUAL_PROFILE.pointFirstPitchBias()));
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(flightYaw + 90.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-flightPitch + 90.0F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(tumbleYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tumblePitch));
        }
        float renderScale = getThrownRenderScale(stack);
        matrices.scale(renderScale, renderScale, renderScale);

        this.itemRenderer.renderItem(
            stack,
            ModelTransformationMode.FIXED,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices,
            vertexConsumers,
            entity.getWorld(),
            entity.getId()
        );

        matrices.pop();
        super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(ThrownSwordEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
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

    private static void spawnTrailTwinkle(ThrownSwordEntity entity, Vec3d interpolatedPosition) {
        if (entity.getVelocity().lengthSquared() < 0.01D) {
            return;
        }

        Integer lastAge = LAST_TWINKLE_AGE.get(entity.getId());
        if (lastAge != null && lastAge == entity.age) {
            return;
        }
        LAST_TWINKLE_AGE.put(entity.getId(), entity.age);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.world.random.nextInt(100) >= 5) {
            return;
        }

        double spread = 0.08D;
        client.world.addParticle(
            ParticleTypes.GLOW,
            interpolatedPosition.x + client.world.random.nextGaussian() * spread,
            interpolatedPosition.y - 0.2D + client.world.random.nextGaussian() * spread,
            interpolatedPosition.z + client.world.random.nextGaussian() * spread,
            0.0D,
            0.02D,
            0.0D
        );
    }

    private static void renderTrail(
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        Vec3d entityPosition,
        Vec3d cameraPosition,
        List<Vec3d> trailPoints,
        float width,
        float alphaScale
    ) {
        int trailColor = SwordThrowClientConfig.get().trailColor();
        List<Vec3d> points = getSmoothedTrailPoints(trailPoints, entityPosition);
        if (points.size() < 2) {
            return;
        }

        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer consumer = vertexConsumers.getBuffer(TRAIL_LAYER);
        Vec3d currentPos = points.get(points.size() - 1);
        Vec3d previousSide = null;
        for (int index = 0; index < points.size() - 1; index++) {
            Vec3d point = points.get(index);
            Vec3d nextPoint = points.get(index + 1);

            float progress0 = index / (float) (points.size() - 1);
            float progress1 = (index + 1) / (float) (points.size() - 1);
            previousSide = emitTrailSegment(entry, consumer, cameraPosition, currentPos, point, nextPoint, width, alphaScale, progress0, progress1, trailColor, previousSide);
        }
    }

    private static List<Vec3d> getSmoothedTrailPoints(List<Vec3d> trailPoints, Vec3d entityPosition) {
        if (trailPoints.size() < 2) {
            return trailPoints;
        }

        List<SeamlessVec3> sourcePoints = new ArrayList<>(trailPoints.size());
        for (Vec3d point : trailPoints) {
            sourcePoints.add(fromMinecraftVec(point));
        }

        List<SeamlessVec3> smoothed = TrailMath.smoothCatmullRom(
            sourcePoints,
            TRAIL_SUBDIVISIONS,
            fromMinecraftVec(entityPosition)
        );

        List<Vec3d> converted = new ArrayList<>(smoothed.size());
        for (SeamlessVec3 point : smoothed) {
            converted.add(toMinecraftVec(point));
        }
        return converted;
    }

    private static Vec3d emitTrailSegment(
        MatrixStack.Entry entry,
        VertexConsumer consumer,
        Vec3d cameraPosition,
        Vec3d origin,
        Vec3d worldStart,
        Vec3d worldEnd,
        float width,
        float alphaScale,
        float progress0,
        float progress1,
        int trailColor,
        Vec3d previousSide
    ) {
        Vec3d startToEnd = worldEnd.subtract(worldStart);
        if (startToEnd.lengthSquared() < 1.0E-5D) {
            return previousSide;
        }

        Vec3d midpoint = worldStart.add(worldEnd).multiply(0.5D);
        Vec3d cameraToMid = cameraPosition.subtract(midpoint);
        Vec3d side = startToEnd.crossProduct(cameraToMid);

        if (side.lengthSquared() < 1.0E-5D) {
            side = startToEnd.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        }

        if (side.lengthSquared() < 1.0E-5D) {
            return previousSide;
        }

        side = side.normalize();
        if (previousSide != null && side.dotProduct(previousSide) < 0.0D) {
            side = side.multiply(-1.0D);
        }
        Vec3d sideStart = side.multiply(TrailMath.widthAtProgress(width, progress0));
        Vec3d sideEnd = side.multiply(TrailMath.widthAtProgress(width, progress1));

        Vec3d start = worldStart.subtract(origin);
        Vec3d end = worldEnd.subtract(origin);
        Vec3d startLeft = start.add(sideStart);
        Vec3d startRight = start.subtract(sideStart);
        Vec3d endLeft = end.add(sideEnd);
        Vec3d endRight = end.subtract(sideEnd);

        int alphaStart = Math.max(24, Math.round(255.0F * TrailMath.alphaAtProgress(alphaScale, progress0)));
        int alphaEnd = Math.max(18, Math.round(255.0F * TrailMath.alphaAtProgress(alphaScale, progress1)));
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        putVertex(consumer, entry, startLeft, trailColor, alphaStart, 0.0F, progress0, light);
        putVertex(consumer, entry, endLeft, trailColor, alphaEnd, 0.0F, progress1, light);
        putVertex(consumer, entry, endRight, trailColor, alphaEnd, 1.0F, progress1, light);
        putVertex(consumer, entry, startRight, trailColor, alphaStart, 1.0F, progress0, light);
        return side;
    }

    private static Vec3d getInterpolatedPosition(ThrownSwordEntity entity, float tickDelta) {
        return new Vec3d(
            MathHelper.lerp(tickDelta, entity.prevX, entity.getX()),
            MathHelper.lerp(tickDelta, entity.prevY, entity.getY()),
            MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ())
        );
    }

    private static SeamlessVec3 fromMinecraftVec(Vec3d vec) {
        return new SeamlessVec3(vec.x, vec.y, vec.z);
    }

    private static Vec3d toMinecraftVec(SeamlessVec3 vec) {
        return new Vec3d(vec.x(), vec.y(), vec.z());
    }

    private static void putVertex(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d position, int color, int alpha, float u, float v, int light) {
        Matrix4f positionMatrix = entry.getPositionMatrix();
        consumer.vertex(positionMatrix, (float) position.x, (float) position.y, (float) position.z)
            .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(entry, 0.0F, 1.0F, 0.0F);
    }
}