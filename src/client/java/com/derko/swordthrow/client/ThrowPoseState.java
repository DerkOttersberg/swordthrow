package com.derko.swordthrow.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class ThrowPoseState {
    private static final int RELEASE_TICKS = 5;

    private static float chargeProgress;
    private static int releaseTicksRemaining;

    private ThrowPoseState() {
    }

    public static void beginCharge() {
        chargeProgress = 0.0F;
        releaseTicksRemaining = 0;
    }

    public static void setChargeProgress(float progress) {
        chargeProgress = MathHelper.clamp(progress, 0.0F, 1.0F);
    }

    public static void releaseForward() {
        releaseTicksRemaining = RELEASE_TICKS;
    }

    public static void cancel() {
        chargeProgress = 0.0F;
        releaseTicksRemaining = 0;
    }

    public static void tick() {
        if (releaseTicksRemaining > 0) {
            releaseTicksRemaining--;
            if (releaseTicksRemaining == 0) {
                chargeProgress = 0.0F;
            }
        }
    }

    public static void applyFirstPersonPose(AbstractClientPlayerEntity player, Hand hand, MatrixStack matrices, float tickDelta) {
        if (hand != Hand.MAIN_HAND) {
            return;
        }

        boolean hasCharge = chargeProgress > 0.001F;
        boolean releasing = releaseTicksRemaining > 0;
        if (!hasCharge && !releasing) {
            return;
        }

        Arm handArm = player.getMainArm();
        float side = handArm == Arm.RIGHT ? 1.0F : -1.0F;
        float easedCharge = chargeProgress * chargeProgress;

        float releaseProgress = 0.0F;
        if (releasing) {
            float raw = 1.0F - ((releaseTicksRemaining - tickDelta) / RELEASE_TICKS);
            float clamped = MathHelper.clamp(raw, 0.0F, 1.0F);
            releaseProgress = 1.0F - (1.0F - clamped) * (1.0F - clamped);
        }

        float backAmount = easedCharge * (1.0F - releaseProgress);

        matrices.translate(side * 0.05F * backAmount, -0.04F * backAmount, 0.18F * backAmount - 0.32F * releaseProgress);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (20.0F * backAmount - 6.0F * releaseProgress)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0F * backAmount + 95.0F * releaseProgress));
    }
}
