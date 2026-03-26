package com.derko.swordthrow.client.render;

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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class ThrownSwordRenderer extends EntityRenderer<ThrownSwordEntity, ThrownSwordRenderer.ThrownSwordRenderState> {
    private static final Identifier TRAIL_TEXTURE = SwordThrowMod.id("textures/effect/sword_trail.png");
    private static final RenderLayer TRAIL_LAYER = RenderLayers.entityTranslucentEmissive(TRAIL_TEXTURE);
    private static final int TRAIL_COLOR = 0xD4A63A;
    private static final float OUTER_TRAIL_WIDTH = 0.15F;
    private static final float INNER_TRAIL_WIDTH = OUTER_TRAIL_WIDTH / 1.5F;
    private static final int TRAIL_SUBDIVISIONS = 4;
    private static final float THROWN_SWORD_SCALE = 0.96F;
    private static final float FLIGHT_SPIN_SPEED = 34.0F;
    private static final int TUMBLE_DURATION_TICKS = 14;
    private static final double TUMBLE_TRIGGER_DOT = 0.55D;
    private static final double TUMBLE_MIN_SPEED_SQUARED = 0.16D;
    private static final Map<Integer, Integer> LAST_TWINKLE_AGE = new HashMap<>();
    private static final Map<Integer, SpinState> SPIN_STATES = new HashMap<>();
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

        if (state.trailPoints.size() > 1) {
            renderTrail(state, matrices, queue, cameraState, OUTER_TRAIL_WIDTH, 0.95F);
            renderTrail(state, matrices, queue, cameraState, INNER_TRAIL_WIDTH, 0.63F);
            spawnTrailTwinkle(state);
        }

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.flightYaw + 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-state.flightPitch + 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.roll));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.tumbleYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.tumblePitch));
        matrices.scale(THROWN_SWORD_SCALE, THROWN_SWORD_SCALE, THROWN_SWORD_SCALE);

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
        state.trailPoints = entity.getTrailPoints();

        Vec3d velocity = entity.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        state.flightYaw = horizontalSpeed > 1.0E-5D ? (float)Math.toDegrees(Math.atan2(velocity.x, velocity.z)) : entity.getYaw();
        state.flightPitch = velocity.lengthSquared() > 1.0E-5D ? (float)Math.toDegrees(Math.atan2(velocity.y, horizontalSpeed)) : entity.getPitch();
        state.entityId = entity.getId();
        state.entityAge = entity.age;
        state.velocitySquared = velocity.lengthSquared();
        updateSpinState(state, velocity, tickDelta);
    }

    private static void updateSpinState(ThrownSwordRenderState state, Vec3d velocity, float tickDelta) {
        SpinState spinState = SPIN_STATES.computeIfAbsent(state.entityId, ignored -> new SpinState());
        if (spinState.lastProcessedAge != state.entityAge) {
            advanceSpinState(spinState, state, velocity);
            spinState.lastProcessedAge = state.entityAge;
        }

        float partialTicks = tickDelta;
        state.roll = spinState.baseRoll + spinState.rollSpeed * partialTicks;
        state.tumbleYaw = spinState.baseYaw + spinState.yawSpeed * partialTicks;
        state.tumblePitch = spinState.basePitch + spinState.pitchSpeed * partialTicks;
    }

    private static void advanceSpinState(SpinState spinState, ThrownSwordRenderState state, Vec3d velocity) {
        double speedSquared = velocity.lengthSquared();
        if (spinState.lastVelocity.lengthSquared() >= TUMBLE_MIN_SPEED_SQUARED && speedSquared >= TUMBLE_MIN_SPEED_SQUARED) {
            double alignment = spinState.lastVelocity.normalize().dotProduct(velocity.normalize());
            if (alignment < TUMBLE_TRIGGER_DOT) {
                spinState.tumbleTicksRemaining = TUMBLE_DURATION_TICKS;
                spinState.rollSpeed = randomSigned(state.entityId, state.entityAge, 0) * 24.0F;
                spinState.yawSpeed = randomSigned(state.entityId, state.entityAge, 1) * 42.0F;
                spinState.pitchSpeed = randomSigned(state.entityId, state.entityAge, 2) * 42.0F;
            }
        }

        if (spinState.tumbleTicksRemaining > 0) {
            spinState.baseRoll += spinState.rollSpeed;
            spinState.baseYaw += spinState.yawSpeed;
            spinState.basePitch += spinState.pitchSpeed;
            spinState.rollSpeed *= 0.9F;
            spinState.yawSpeed *= 0.9F;
            spinState.pitchSpeed *= 0.9F;
            spinState.tumbleTicksRemaining--;
        } else {
            spinState.baseRoll += FLIGHT_SPIN_SPEED;
            spinState.baseYaw *= 0.75F;
            spinState.basePitch *= 0.75F;
            spinState.yawSpeed = 0.0F;
            spinState.pitchSpeed = 0.0F;
            spinState.rollSpeed = FLIGHT_SPIN_SPEED;
        }

        spinState.lastVelocity = velocity;
    }

    private static float randomSigned(int entityId, int entityAge, int salt) {
        int hash = entityId * 73428767 ^ entityAge * 9122713 ^ salt * 19349663;
        hash ^= hash >>> 16;
        return ((hash & 0xFFFF) / 32767.5F) - 1.0F;
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
                emitTrailSegment(entry, consumer, cameraState, currentPos, point, nextPoint, width, alphaScale, progress0, progress1);
            }
        });
    }

    private static List<Vec3d> getSmoothedTrailPoints(ThrownSwordRenderState state) {
        if (state.trailPoints.size() < 2) {
            return state.trailPoints;
        }

        List<Vec3d> points = new ArrayList<>(state.trailPoints.size());
        for (Vec3d point : state.trailPoints) {
            points.add(point);
        }

        points.set(points.size() - 1, new Vec3d(state.x, state.y, state.z));

        if (points.size() < 3) {
            return points;
        }

        List<Vec3d> smoothed = new ArrayList<>((points.size() - 1) * TRAIL_SUBDIVISIONS + 1);
        smoothed.add(points.getFirst());

        for (int index = 0; index < points.size() - 1; index++) {
            Vec3d previous = index > 0 ? points.get(index - 1) : points.get(index);
            Vec3d current = points.get(index);
            Vec3d next = points.get(index + 1);
            Vec3d following = index + 2 < points.size() ? points.get(index + 2) : next;

            for (int step = 1; step <= TRAIL_SUBDIVISIONS; step++) {
                float delta = step / (float)TRAIL_SUBDIVISIONS;
                smoothed.add(catmullRom(previous, current, next, following, delta));
            }
        }
        return smoothed;
    }

    private static Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, float t) {
        double t2 = t * t;
        double t3 = t2 * t;

        return new Vec3d(
            0.5D * ((2.0D * p1.x) + (-p0.x + p2.x) * t + (2.0D * p0.x - 5.0D * p1.x + 4.0D * p2.x - p3.x) * t2 + (-p0.x + 3.0D * p1.x - 3.0D * p2.x + p3.x) * t3),
            0.5D * ((2.0D * p1.y) + (-p0.y + p2.y) * t + (2.0D * p0.y - 5.0D * p1.y + 4.0D * p2.y - p3.y) * t2 + (-p0.y + 3.0D * p1.y - 3.0D * p2.y + p3.y) * t3),
            0.5D * ((2.0D * p1.z) + (-p0.z + p2.z) * t + (2.0D * p0.z - 5.0D * p1.z + 4.0D * p2.z - p3.z) * t2 + (-p0.z + 3.0D * p1.z - 3.0D * p2.z + p3.z) * t3)
        );
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
        float progress1
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
        Vec3d sideStart = side.multiply(trailWidth(width, progress0));
        Vec3d sideEnd = side.multiply(trailWidth(width, progress1));

        Vec3d start = worldStart.subtract(origin);
        Vec3d end = worldEnd.subtract(origin);
        Vec3d startLeft = start.add(sideStart);
        Vec3d startRight = start.subtract(sideStart);
        Vec3d endLeft = end.add(sideEnd);
        Vec3d endRight = end.subtract(sideEnd);

        int alphaStart = Math.max(0, Math.round(255.0F * trailAlpha(alphaScale, progress0)));
        int alphaEnd = Math.max(0, Math.round(255.0F * trailAlpha(alphaScale, progress1)));
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        putVertex(consumer, entry, startLeft, TRAIL_COLOR, alphaStart, 0.0F, progress0, light);
        putVertex(consumer, entry, endLeft, TRAIL_COLOR, alphaEnd, 0.0F, progress1, light);
        putVertex(consumer, entry, endRight, TRAIL_COLOR, alphaEnd, 1.0F, progress1, light);
        putVertex(consumer, entry, startRight, TRAIL_COLOR, alphaStart, 1.0F, progress0, light);
    }

    private static float trailWidth(float baseWidth, float progress) {
        return (float)Math.sqrt(Math.max(progress, 0.0F)) * baseWidth;
    }

    private static float trailAlpha(float alphaScale, float progress) {
        return (float)Math.cbrt(Math.max(0.0F, alphaScale * progress - 0.1F));
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
        public int entityId;
        public int entityAge;
        public float flightYaw;
        public float flightPitch;
        public float roll;
        public float tumbleYaw;
        public float tumblePitch;
        public double velocitySquared;
        public List<Vec3d> trailPoints = Collections.emptyList();
    }

    private static final class SpinState {
        private Vec3d lastVelocity = Vec3d.ZERO;
        private int lastProcessedAge = Integer.MIN_VALUE;
        private int tumbleTicksRemaining;
        private float baseRoll;
        private float baseYaw;
        private float basePitch;
        private float rollSpeed = FLIGHT_SPIN_SPEED;
        private float yawSpeed;
        private float pitchSpeed;
    }
}