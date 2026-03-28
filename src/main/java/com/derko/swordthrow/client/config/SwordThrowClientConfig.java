package com.derko.swordthrow.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

public final class SwordThrowClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("swordthrow-client.json");

    private static final TrailColorOption[] TRAIL_COLOR_OPTIONS = new TrailColorOption[] {
        new TrailColorOption("Amber", 0xD4A63A),
        new TrailColorOption("Crimson", 0xCF3B3B),
        new TrailColorOption("Emerald", 0x3BCF78),
        new TrailColorOption("Arcane Blue", 0x4A89FF),
        new TrailColorOption("Violet", 0x9F53FF),
        new TrailColorOption("Frost", 0x86E8FF),
        new TrailColorOption("White", 0xFFFFFF)
    };

    private static ConfigData data = new ConfigData();

    private SwordThrowClientConfig() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            if (loaded != null) {
                data = loaded;
            }
            sanitize();
        } catch (IOException ex) {
            sanitize();
        }
    }

    public static void save() {
        sanitize();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static ConfigData get() {
        sanitize();
        return data;
    }

    public static void set(ConfigData nextData) {
        data = nextData;
        sanitize();
        save();
    }

    public static TrailColorOption[] trailColorOptions() {
        return TRAIL_COLOR_OPTIONS.clone();
    }

    public static int nextTrailColor(int currentColor) {
        int currentIndex = 0;
        for (int i = 0; i < TRAIL_COLOR_OPTIONS.length; i++) {
            if (TRAIL_COLOR_OPTIONS[i].rgb() == currentColor) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % TRAIL_COLOR_OPTIONS.length;
        return TRAIL_COLOR_OPTIONS[nextIndex].rgb();
    }

    public static String colorLabel(int rgb) {
        for (TrailColorOption option : TRAIL_COLOR_OPTIONS) {
            if (option.rgb() == rgb) {
                return option.label();
            }
        }
        return "Custom";
    }

    private static void sanitize() {
        if (data == null) {
            data = new ConfigData();
        }

        boolean colorFound = false;
        for (TrailColorOption option : TRAIL_COLOR_OPTIONS) {
            if (option.rgb() == data.trailColor) {
                colorFound = true;
                break;
            }
        }

        if (!colorFound) {
            data.trailColor = TRAIL_COLOR_OPTIONS[0].rgb();
        }
    }

    public record TrailColorOption(String label, int rgb) {
    }

    public static final class ConfigData {
        private boolean thirdPersonAnimationsEnabled = true;
        private boolean trailEffectEnabled = true;
        private int trailColor = 0xD4A63A;

        public ConfigData() {
        }

        public ConfigData(boolean thirdPersonAnimationsEnabled, boolean trailEffectEnabled, int trailColor) {
            this.thirdPersonAnimationsEnabled = thirdPersonAnimationsEnabled;
            this.trailEffectEnabled = trailEffectEnabled;
            this.trailColor = trailColor;
        }

        public boolean thirdPersonAnimationsEnabled() {
            return thirdPersonAnimationsEnabled;
        }

        public boolean trailEffectEnabled() {
            return trailEffectEnabled;
        }

        public int trailColor() {
            return trailColor;
        }
    }
}

