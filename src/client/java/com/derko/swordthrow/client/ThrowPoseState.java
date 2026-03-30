package com.derko.swordthrow.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class ThrowPoseState {
    private static final int RELEASE_TICKS = 6;
    private static final float CHARGE_RESPONSE = 0.30F;
    private static final float MAIN_HAND_BASE_LIFT = 0.06F;
    private static final float OFF_HAND_BASE_LIFT = 0.43F;

    private static final FloatDriver chargeDriver = new FloatDriver();

    private static float chargeTarget;
    private static float releaseStartCharge;
    private static int releaseTicksRemaining;

    private ThrowPoseState() {
    }

    public static void beginCharge() {
        chargeTarget = 0.0F;
        releaseStartCharge = 0.0F;
        chargeDriver.reset(0.0F);
        releaseTicksRemaining = 0;
    }

    public static void setChargeProgress(float progress) {
        chargeTarget = MathHelper.clamp(progress, 0.0F, 1.0F);
    }

    public static void releaseForward() {
        releaseStartCharge = getChargeProgress(1.0F);
        chargeTarget = 0.0F;
        releaseTicksRemaining = RELEASE_TICKS;
    }

    public static void cancel() {
        chargeTarget = 0.0F;
        releaseStartCharge = 0.0F;
        chargeDriver.reset(0.0F);
        releaseTicksRemaining = 0;
    }

    public static void tick() {
        chargeDriver.tick(chargeTarget, CHARGE_RESPONSE);

        if (releaseTicksRemaining > 0) {
            releaseTicksRemaining--;
            if (releaseTicksRemaining == 0) {
                releaseStartCharge = 0.0F;
                if (chargeTarget < 0.001F) {
                    chargeDriver.reset(0.0F);
                }
            }
        }
    }

    public static boolean isOffHandVisible() {
        return getSupportPresence(getChargeProgress(1.0F)) > 0.001F || releaseTicksRemaining > 0;
    }

    public static boolean isChargeIndicatorVisible() {
        return chargeTarget > 0.001F && releaseTicksRemaining == 0;
    }

    public static float getChargeIndicatorProgress(float tickDelta) {
        return MathHelper.clamp(getChargeProgress(tickDelta), 0.0F, 1.0F);
    }

    public static void applyMainHandPose(AbstractClientPlayerEntity player, MatrixStack matrices, float tickDelta) {
        PoseSample pose = sample(player, player.getMainArm(), tickDelta);
        if (!pose.visible()) return;

        matrices.translate(
            pose.side() * 0.06F * pose.windUp() - pose.side() * 0.018F * pose.release() + pose.side() * 0.010F * pose.mainSwaySide(),
            MAIN_HAND_BASE_LIFT + 0.27F * pose.windUp() - 0.025F * pose.release() + 0.012F * pose.mainSwayLift(),
            0.12F * pose.windUp() - 0.28F * pose.release() - 0.010F * pose.mainSwayDepth()
        );
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.side() * (12.0F * pose.windUp() - 5.0F * pose.release() + 2.5F * pose.mainSwaySide())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-126.0F * pose.windUp() + 38.0F * pose.release() - 2.0F * pose.mainSwayLift()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-pose.side() * 12.0F * pose.windUp() + pose.side() * 9.0F * pose.release() + pose.side() * 4.0F * pose.mainSwayRoll()));
    }

    public static void applyOffHandAimContext(AbstractClientPlayerEntity player, MatrixStack matrices, Arm offArm, float tickDelta) {
        PoseSample pose = sample(player, offArm.getOpposite(), tickDelta);
        if (!pose.visible()) return;

        float presence = pose.offHandPresence();
        float hidden = 1.0F - presence;
        float extend = pose.offHandExtend();
        float drop = pose.offHandRecoil();

        matrices.translate(
            pose.offHandSide() * 0.24F * hidden - pose.offHandSide() * 0.10F * extend + pose.offHandSide() * 0.004F * pose.offSwaySide(),
            OFF_HAND_BASE_LIFT + -0.04F * hidden + 0.55F * extend - 0.18F * drop + 0.006F * pose.offSwayLift(),
            0.06F * hidden - 0.36F * extend - 0.006F * pose.offSwayDepth()
        );
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.offHandSide() * (-34.0F * hidden + 40.0F * extend + 2.0F * pose.offSwaySide())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(10.0F * hidden - 68.0F * extend + 8.0F * drop - 1.0F * pose.offSwayLift()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.offHandSide() * (-14.0F * hidden - 14.0F * extend + 2.5F * pose.offSwayRoll())));
    }

    public static void applyThirdPersonOffHandPose(float age, Arm offArmSide, ModelPart offArm) {
        if (offArmSide == null) {
            return;
        }

        PoseSample pose = sample(age, offArmSide.getOpposite(), age - MathHelper.floor(age));
        float support = Math.max(pose.offHandPresence(), pose.offHandExtend());
        if (support <= 0.001F && pose.offHandRecoil() <= 0.001F) {
            return;
        }

        float side = pose.offHandSide();
        float recoil = pose.offHandRecoil();
        float armPitch = -1.35F * support + 0.24F * recoil;
        float armYaw = side * (-0.42F * support + 0.08F * recoil);
        float armRoll = side * (-0.10F * support + 0.03F * recoil);

        offArm.pitch += armPitch;
        offArm.yaw += armYaw;
        offArm.roll += armRoll;
    }

    public static void applyThirdPersonMainHandPose(float age, Arm mainArmSide, ModelPart mainArm) {
        if (mainArmSide == null) {
            return;
        }

        PoseSample pose = sample(age, mainArmSide, age - MathHelper.floor(age));
        if (pose.windUp() <= 0.001F && pose.release() <= 0.001F) {
            return;
        }

        float side = pose.side();
        float armPitch = -1.56F * pose.windUp() + 0.44F * pose.release() - 0.05F * pose.mainSwayLift();
        float armYaw = side * (0.18F * pose.windUp() - 0.08F * pose.release() + 0.04F * pose.mainSwaySide());
        float armRoll = side * (-0.18F * pose.windUp() + 0.12F * pose.release() + 0.04F * pose.mainSwayRoll());

        mainArm.pitch += armPitch;
        mainArm.yaw += armYaw;
        mainArm.roll += armRoll;
    }

    private static float getChargeProgress(float tickDelta) {
        return chargeDriver.get(tickDelta);
    }

    private static float getReleaseProgress(float tickDelta) {
        if (releaseTicksRemaining <= 0) {
            return 0.0F;
        }

        float raw = 1.0F - ((releaseTicksRemaining - tickDelta) / RELEASE_TICKS);
        return ease(MathHelper.clamp(raw, 0.0F, 1.0F));
    }

    private static float ease(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static PoseSample sample(AbstractClientPlayerEntity player, Arm mainArm, float tickDelta) {
        return sample(player.age + tickDelta, mainArm, tickDelta);
    }

    private static PoseSample sample(float age, Arm mainArm, float tickDelta) {
        float charge = ease(getChargeProgress(tickDelta));
        float release = getReleaseProgress(tickDelta);
        float releaseStrength = ease(Math.max(releaseStartCharge, charge));
        float releaseVisibility = 1.0F - release;
        float supportPresence = getSupportPresence(charge) * releaseVisibility;
        float time = age;
        float swayWeight = supportPresence * (1.0F - release * 0.75F) * 0.55F;
        float offSwayWeight = supportPresence * (1.0F - release * 0.75F) * 0.24F;
        float mainSwaySide = MathHelper.sin(time * 0.18F) * swayWeight;
        float mainSwayLift = MathHelper.cos(time * 0.13F) * swayWeight;
        float mainSwayDepth = MathHelper.sin(time * 0.11F + 0.8F) * swayWeight;
        float mainSwayRoll = MathHelper.cos(time * 0.16F + 0.45F) * swayWeight;
        float offSwaySide = MathHelper.sin(time * 0.13F + 1.1F) * offSwayWeight * 0.45F;
        float offSwayLift = MathHelper.cos(time * 0.11F + 0.4F) * offSwayWeight * 0.20F;
        float offSwayDepth = MathHelper.sin(time * 0.09F + 2.0F) * offSwayWeight * 0.22F;
        float offSwayRoll = MathHelper.cos(time * 0.12F + 1.7F) * offSwayWeight * 0.55F;

        float windUp = charge * (1.0F - release * 0.82F);
        float snap = release * (0.42F + 0.58F * releaseStrength);
        float heldCharge = Math.max(charge, releaseStartCharge * releaseVisibility);
        float heldOffHandCharge = easeIntoHalfCharge(heldCharge);
        float offHandExtend = getSupportPresence(heldOffHandCharge) * (0.35F + 0.65F * heldOffHandCharge) * releaseVisibility;
        float offHandRecoil = release * release;

        return new PoseSample(
            mainArm == Arm.RIGHT ? 1.0F : -1.0F,
            mainArm == Arm.RIGHT ? -1.0F : 1.0F,
            windUp,
            snap,
            supportPresence,
            offHandExtend,
            offHandRecoil,
            mainSwaySide,
            mainSwayLift,
            mainSwayDepth,
            mainSwayRoll,
            offSwaySide,
            offSwayLift,
            offSwayDepth,
            offSwayRoll
        );
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float normalized = MathHelper.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float easeIntoHalfCharge(float charge) {
        float cappedCharge = MathHelper.clamp(charge, 0.0F, 1.0F);
        if (cappedCharge >= 0.5F) {
            return 0.5F;
        }

        float remaining = 0.5F - cappedCharge;
        return 0.5F - (remaining * remaining) / 0.5F;
    }

    private static float getSupportPresence(float charge) {
        return smoothStep(0.10F, 0.28F, charge);
    }

    private record PoseSample(
        float side,
        float offHandSide,
        float windUp,
        float release,
        float offHandPresence,
        float offHandExtend,
        float offHandRecoil,
        float mainSwaySide,
        float mainSwayLift,
        float mainSwayDepth,
        float mainSwayRoll,
        float offSwaySide,
        float offSwayLift,
        float offSwayDepth,
        float offSwayRoll
    ) {
        private boolean visible() {
            return windUp > 0.001F || release > 0.001F || offHandPresence > 0.001F || offHandExtend > 0.001F || offHandRecoil > 0.001F;
        }
    }

    private static final class FloatDriver {
        private float previousValue;
        private float currentValue;

        private void reset(float value) {
            previousValue = value;
            currentValue = value;
        }

        private void tick(float targetValue, float response) {
            previousValue = currentValue;
            currentValue = MathHelper.lerp(response, currentValue, targetValue);
            if (Math.abs(currentValue - targetValue) < 0.0005F) {
                currentValue = targetValue;
            }
        }

        private float get(float tickDelta) {
            return MathHelper.lerp(tickDelta, previousValue, currentValue);
        }
    }
}
