package com.derko.swordthrow.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SwordThrowConfigScreen extends Screen {
    private final Screen parent;

    private boolean thirdPersonAnimationsEnabled;
    private boolean trailEffectEnabled;
    private int trailColor;

    private Button thirdPersonButton;
    private Button trailButton;
    private Button trailColorButton;

    public SwordThrowConfigScreen(Screen parent) {
        super(Component.literal("Sword Throw Settings"));
        this.parent = parent;

        SwordThrowClientConfig.ConfigData data = SwordThrowClientConfig.get();
        this.thirdPersonAnimationsEnabled = data.thirdPersonAnimationsEnabled();
        this.trailEffectEnabled = data.trailEffectEnabled();
        this.trailColor = data.trailColor();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 4;

        this.thirdPersonButton = Button.builder(getThirdPersonText(), button -> {
            this.thirdPersonAnimationsEnabled = !this.thirdPersonAnimationsEnabled;
            refreshLabels();
        }).bounds(centerX - 110, y, 220, 20).build();
        this.addRenderableWidget(this.thirdPersonButton);

        this.trailButton = Button.builder(getTrailEffectText(), button -> {
            this.trailEffectEnabled = !this.trailEffectEnabled;
            refreshLabels();
        }).bounds(centerX - 110, y + 24, 220, 20).build();
        this.addRenderableWidget(this.trailButton);

        this.trailColorButton = Button.builder(getTrailColorText(), button -> {
            this.trailColor = SwordThrowClientConfig.nextTrailColor(this.trailColor);
            refreshLabels();
        }).bounds(centerX - 110, y + 48, 220, 20).build();
        this.addRenderableWidget(this.trailColorButton);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> saveAndClose())
            .bounds(centerX - 110, y + 84, 108, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
            .bounds(centerX + 2, y + 84, 108, 20)
            .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.literal("Configure throw visuals and animation behavior"), this.width / 2, 36, 0xAFAFAF);
    }

    private void saveAndClose() {
        SwordThrowClientConfig.set(new SwordThrowClientConfig.ConfigData(
            this.thirdPersonAnimationsEnabled,
            this.trailEffectEnabled,
            this.trailColor
        ));
        onClose();
    }

    private void refreshLabels() {
        this.thirdPersonButton.setMessage(getThirdPersonText());
        this.trailButton.setMessage(getTrailEffectText());
        this.trailColorButton.setMessage(getTrailColorText());
    }

    private Component getThirdPersonText() {
        return Component.literal("Third-Person Animations: " + onOff(this.thirdPersonAnimationsEnabled));
    }

    private Component getTrailEffectText() {
        return Component.literal("Trail Effect: " + onOff(this.trailEffectEnabled));
    }

    private Component getTrailColorText() {
        return Component.literal("Trail Color: " + SwordThrowClientConfig.colorLabel(this.trailColor));
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
