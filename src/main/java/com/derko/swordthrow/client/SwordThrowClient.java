package com.derko.swordthrow.client;

import com.derko.swordthrow.entity.ModEntities;
import com.derko.swordthrow.client.config.SwordThrowClientConfig;
import com.derko.swordthrow.network.ThrowSwordPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.derko.swordthrow.client.render.ThrownSwordRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SwordThrowClient {
    private static final int MAX_CHARGE_TICKS = 30;
    private static final int CHARGE_BAR_WIDTH = 24;
    private static final int CHARGE_BAR_HEIGHT = 3;

    private static boolean charging;
    private static int chargeTicks;
    private static boolean allowNextSingleItemDrop;

    private SwordThrowClient() {
    }

    public static void init(IEventBus modEventBus) {
        SwordThrowClientConfig.load();
        modEventBus.addListener(SwordThrowClient::registerEntityRenderers);
        NeoForge.EVENT_BUS.addListener(SwordThrowClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SwordThrowClient::onRenderGui);
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.THROWN_SWORD.get(), ThrownSwordRenderer::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ThrowPoseState.tick();

        if (client.player == null || client.level == null || client.player.isSpectator()) {
            charging = false;
            chargeTicks = 0;
            allowNextSingleItemDrop = false;
            ThrowPoseState.cancel();
            return;
        }

        ItemStack heldStack = client.player.getMainHandItem();
        boolean keyDown = client.options.keyDrop.isDown() && client.screen == null;
        boolean canThrowHeldItem = canThrow(heldStack) && !client.player.getCooldowns().isOnCooldown(heldStack.getItem());

        if (keyDown && canThrowHeldItem) {
            if (!charging) {
                charging = true;
                chargeTicks = 0;
                ThrowPoseState.beginCharge();
            }
            charging = true;
            chargeTicks = Math.min(chargeTicks + 1, MAX_CHARGE_TICKS);
            ThrowPoseState.setChargeProgress(chargeTicks / (float) MAX_CHARGE_TICKS);
            return;
        }

        if (charging) {
            if (chargeTicks >= MAX_CHARGE_TICKS / 2) {
                ThrowPoseState.releaseForward();
                PacketDistributor.sendToServer(new ThrowSwordPayload(chargeTicks));
            } else {
                ThrowPoseState.cancel();
                allowNormalSingleItemDrop(client);
            }
            charging = false;
            chargeTicks = 0;
        }
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ThrowPoseState.isChargeIndicatorVisible()) {
            return;
        }
        renderChargeBar(event.getGuiGraphics());
    }

    private static void renderChargeBar(GuiGraphics guiGraphics) {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        int left = screenWidth / 2 - CHARGE_BAR_WIDTH / 2;
        int top = screenHeight / 2 + 12;
        int right = left + CHARGE_BAR_WIDTH;
        int bottom = top + CHARGE_BAR_HEIGHT;

        float progress = ThrowPoseState.getChargeIndicatorProgress(1.0F);
        int fillWidth = Math.max(1, Math.round((CHARGE_BAR_WIDTH - 2) * progress));
        int fillColor = progress >= 0.5F ? 0xFFDDD37A : 0xFFC96A6A;

        guiGraphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xAA111111);
        guiGraphics.fill(left, top, right, bottom, 0xCC2A2A2A);
        guiGraphics.fill(left + 1, top + 1, left + 1 + fillWidth, bottom - 1, fillColor);
    }

    private static boolean canThrow(ItemStack stack) {
        return !stack.isEmpty() && !isVanillaTrident(stack);
    }

    public static boolean shouldInterceptDropKey(Minecraft client) {
        return client != null
            && client.screen == null
            && client.player != null
            && client.player.isAlive()
            && !client.player.isSpectator()
            && canThrow(client.player.getMainHandItem());
    }

    public static boolean consumeSingleItemDropBypass() {
        if (!allowNextSingleItemDrop) {
            return false;
        }

        allowNextSingleItemDrop = false;
        return true;
    }

    private static void allowNormalSingleItemDrop(Minecraft client) {
        if (client.player == null || client.screen != null) {
            return;
        }

        allowNextSingleItemDrop = true;
        client.player.drop(false);
    }

    private static boolean isVanillaTrident(ItemStack stack) {
        return stack.getItem() instanceof TridentItem || stack.is(Items.TRIDENT);
    }
}
