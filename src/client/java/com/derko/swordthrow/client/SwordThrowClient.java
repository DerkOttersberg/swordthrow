package com.derko.swordthrow.client;

import com.derko.swordthrow.client.render.ThrownSwordRenderer;
import com.derko.swordthrow.entity.ModEntities;
import com.derko.swordthrow.network.ThrowSwordPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SwordThrowClient implements ClientModInitializer {
    private static final int MAX_CHARGE_TICKS = 30;
    private static final int CHARGE_BAR_WIDTH = 44;
    private static final int CHARGE_BAR_HEIGHT = 5;
    private static final KeyBinding THROW_KEY = KeyBindingHelper.registerKeyBinding(
        new KeyBinding("key.swordthrow.throw", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, KeyBinding.Category.create(Identifier.of("swordthrow", "controls")))
    );

    private static boolean charging;
    private static int chargeTicks;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.THROWN_SWORD, ThrownSwordRenderer::new);

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> renderChargeBar(drawContext));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ThrowPoseState.tick();

            if (client.player == null || client.world == null || client.player.isSpectator()) {
                charging = false;
                chargeTicks = 0;
                ThrowPoseState.cancel();
                return;
            }

            boolean keyDown = THROW_KEY.isPressed();
            boolean holdingSword = client.player.getMainHandStack().isIn(ItemTags.SWORDS);

            if (keyDown && holdingSword) {
                if (!charging) {
                    charging = true;
                    chargeTicks = 0;
                    ThrowPoseState.beginCharge();
                }
                chargeTicks = Math.min(chargeTicks + 1, MAX_CHARGE_TICKS);
                ThrowPoseState.setChargeProgress(chargeTicks / (float) MAX_CHARGE_TICKS);
                return;
            }

            if (charging) {
                if (chargeTicks >= MAX_CHARGE_TICKS / 2) {
                    ThrowPoseState.releaseForward();
                    ClientPlayNetworking.send(new ThrowSwordPayload(chargeTicks));
                } else {
                    ThrowPoseState.cancel();
                }
                charging = false;
                chargeTicks = 0;
            }
        });
    }

    private static void renderChargeBar(DrawContext drawContext) {
        if (!ThrowPoseState.isChargeIndicatorVisible()) {
            return;
        }

        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();
        int left = screenWidth / 2 - CHARGE_BAR_WIDTH / 2;
        int top = screenHeight / 2 + 12;
        int right = left + CHARGE_BAR_WIDTH;
        int bottom = top + CHARGE_BAR_HEIGHT;

        float progress = ThrowPoseState.getChargeIndicatorProgress(1.0F);
        int fillWidth = Math.max(1, Math.round((CHARGE_BAR_WIDTH - 2) * progress));
        int fillColor = progress >= 0.5F ? 0xFFDDD37A : 0xFFC96A6A;

        drawContext.fill(left - 1, top - 1, right + 1, bottom + 1, 0xAA111111);
        drawContext.fill(left, top, right, bottom, 0xCC2A2A2A);
        drawContext.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, fillColor);
    }
}
