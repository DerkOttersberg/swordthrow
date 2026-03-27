package com.derko.swordthrow.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class SwordThrowConfigScreen extends Screen {
    private final Screen parent;

    private boolean thirdPersonAnimationsEnabled;
    private boolean trailEffectEnabled;
    private int trailColor;

    private ButtonWidget thirdPersonButton;
    private ButtonWidget trailButton;
    private ButtonWidget trailColorButton;

    public SwordThrowConfigScreen(Screen parent) {
        super(Text.literal("Sword Throw Settings"));
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

        this.thirdPersonButton = ButtonWidget.builder(getThirdPersonText(), button -> {
            this.thirdPersonAnimationsEnabled = !this.thirdPersonAnimationsEnabled;
            refreshLabels();
        }).dimensions(centerX - 110, y, 220, 20).build();
        this.addDrawableChild(this.thirdPersonButton);

        this.trailButton = ButtonWidget.builder(getTrailEffectText(), button -> {
            this.trailEffectEnabled = !this.trailEffectEnabled;
            refreshLabels();
        }).dimensions(centerX - 110, y + 24, 220, 20).build();
        this.addDrawableChild(this.trailButton);

        this.trailColorButton = ButtonWidget.builder(getTrailColorText(), button -> {
            this.trailColor = SwordThrowClientConfig.nextTrailColor(this.trailColor);
            refreshLabels();
        }).dimensions(centerX - 110, y + 48, 220, 20).build();
        this.addDrawableChild(this.trailColorButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> saveAndClose())
            .dimensions(centerX - 110, y + 84, 108, 20)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.close())
            .dimensions(centerX + 2, y + 84, 108, 20)
            .build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Configure throw visuals and animation behavior"), this.width / 2, 36, 0xAFAFAF);
    }

    private void saveAndClose() {
        SwordThrowClientConfig.set(new SwordThrowClientConfig.ConfigData(
            this.thirdPersonAnimationsEnabled,
            this.trailEffectEnabled,
            this.trailColor
        ));
        close();
    }

    private void refreshLabels() {
        this.thirdPersonButton.setMessage(getThirdPersonText());
        this.trailButton.setMessage(getTrailEffectText());
        this.trailColorButton.setMessage(getTrailColorText());
    }

    private Text getThirdPersonText() {
        return Text.literal("Third-Person Animations: " + onOff(this.thirdPersonAnimationsEnabled));
    }

    private Text getTrailEffectText() {
        return Text.literal("Trail Effect: " + onOff(this.trailEffectEnabled));
    }

    private Text getTrailColorText() {
        return Text.literal("Trail Color: " + SwordThrowClientConfig.colorLabel(this.trailColor));
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
