package com.derko.swordthrow.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class ThrowPoseState {
    private static final int RELEASE_TICKS = 6;
    private static final float CHARGE_RESPONSE = 0.30F;

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
        return Math.max(chargeTarget, getChargeProgress(1.0F)) > 0.001F || releaseTicksRemaining > 0;
    }

    public static boolean isChargeIndicatorVisible() {
        return chargeTarget > 0.001F && releaseTicksRemaining == 0;
    }

    public static float getChargeIndicatorProgress(float tickDelta) {
        return MathHelper.clamp(getChargeProgress(tickDelta), 0.0F, 1.0F);
    }

    public static void applyMainHandPose(AbstractClientPlayerEntity player, MatrixStack matrices, float tickDelta) {
        PoseSample pose = sample(player.getMainArm(), tickDelta);
        if (!pose.visible()) return;

        matrices.translate(
            pose.side() * 0.07F * pose.windUp() - pose.side() * 0.02F * pose.release(),
            0.28F * pose.windUp() - 0.04F * pose.release(),
            0.10F * pose.windUp() - 0.36F * pose.release()
        );
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.side() * (10.0F * pose.windUp() - 5.0F * pose.release())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-124.0F * pose.windUp() + 48.0F * pose.release()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-pose.side() * 8.0F * pose.windUp() + pose.side() * 10.0F * pose.release()));
    }

    public static void applyOffHandAimContext(MatrixStack matrices, Arm offArm, float tickDelta) {
        PoseSample pose = sample(offArm.getOpposite(), tickDelta);
        if (!pose.visible()) return;

        float extend = pose.offHandExtend();
        float recoil = pose.offHandRecoil();

        matrices.translate(
            -pose.offHandSide() * 0.16F * extend + pose.offHandSide() * 0.22F * recoil,
            0.03F * extend - 0.12F * recoil,
            -0.09F * extend + 0.30F * recoil
        );
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.offHandSide() * (10.0F * extend - 24.0F * recoil)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0F * extend + 30.0F * recoil));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.offHandSide() * (-12.0F * extend + 14.0F * recoil)));
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

    private static PoseSample sample(Arm mainArm, float tickDelta) {
        float charge = ease(getChargeProgress(tickDelta));
        float release = getReleaseProgress(tickDelta);
        float releaseStrength = ease(Math.max(releaseStartCharge, charge));

        float windUp = charge * (1.0F - release * 0.82F);
        float snap = release * (0.42F + 0.58F * releaseStrength);
        float offHandExtend = charge * charge * (1.0F - release * 0.88F);
        float offHandRecoil = release * (0.62F + 0.38F * releaseStrength);

        return new PoseSample(
            mainArm == Arm.RIGHT ? 1.0F : -1.0F,
            mainArm == Arm.RIGHT ? -1.0F : 1.0F,
            windUp,
            snap,
            offHandExtend,
            offHandRecoil
        );
    }

    private record PoseSample(float side, float offHandSide, float windUp, float release, float offHandExtend, float offHandRecoil) {
        private boolean visible() {
            return windUp > 0.001F || release > 0.001F || offHandExtend > 0.001F || offHandRecoil > 0.001F;
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
