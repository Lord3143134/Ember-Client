package com.ember.client.ui;

import com.ember.client.Config;
import com.ember.client.Module;
import com.ember.client.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class HudRenderer {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    // {top, middle, horizon} colours per sky preset (RGB)
    private static final int[][] SKIES = {
            {0x1A0505, 0x8B1A0E, 0xE8A67A},   // Inferno
            {0x1B1035, 0x7A2E6B, 0xF2A07B},   // Dusk
            {0x04141F, 0x0F6B6B, 0x7DE8B5},   // Aurora
            {0x000000, 0x1A0A2E, 0x5B2A8A}    // Void
    };
    private static final int[] TINTS = {0xFF8A1F, 0x2F6BFF, 0xFF4D8D, 0x3DD68C};

    // fixed star directions (yaw 0..360, pitch -85..-2 = above the horizon)
    private static final int MAX_STARS = 400;
    private static final float[] STAR_YAW = new float[MAX_STARS], STAR_PITCH = new float[MAX_STARS];
    static {
        Random r = new Random(1234);
        for (int i = 0; i < MAX_STARS; i++) {
            STAR_YAW[i] = r.nextFloat() * 360f;
            STAR_PITCH[i] = -2f - r.nextFloat() * 83f;
        }
    }

    private HudRenderer() {}

    public static void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        if (Modules.sky.enabled) sky(c, mc, sw, sh);
        if (Modules.tint.enabled) {
            int a = ((int) (255 * Modules.tintStrength.num)) << 24;
            c.fill(0, 0, sw, sh, a | TINTS[Modules.tintColor.mode]);
        }
        if (Modules.vignette.enabled) vignette(c, sw, sh);

        if (!Config.hud) return;

        if (Modules.watermark.enabled) {
            Gfx.text(c, "EMBER", 4, 4, Gfx.ACC, 1.5f);
        }
        if (Modules.coords.enabled) {
            String s = String.format("XYZ: %.1f, %.1f, %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Gfx.text(c, s, 4, Modules.watermark.enabled ? 20 : 4, Gfx.TEXT, 0.75f);
        }
        if (Modules.infoBar.enabled) {
            String s = mc.player.getName().getString() + "  |  " + (mc.isInSingleplayer() ? "singleplayer" : "multiplayer")
                    + "  |  " + mc.getCurrentFps() + " fps  |  " + LocalTime.now().format(CLOCK);
            int w = Gfx.width(s, 0.8f) + 12;
            int x = (sw - w) / 2;
            Gfx.roundBorder(c, x, 3, w, 13, 4, 0xC0101012, Gfx.BORDER);
            Gfx.text(c, s, x + 6, 6, Gfx.TEXT, 0.8f);
        }
        if (Modules.keystrokes.enabled) keystrokes(c, mc, sh);
        if (Modules.arrayList.enabled) {
            List<Module> on = new ArrayList<>();
            for (Module m : Modules.all()) if (m.enabled && m.showInHud && Modules.visible(m)) on.add(m);
            on.sort(Comparator.comparingInt((Module m) -> -Gfx.width(m.name, 0.8f)));
            int y = 4;
            for (Module m : on) {
                int w = Gfx.width(m.name, 0.8f);
                c.fill(sw - w - 8, y, sw - 2, y + 9, 0x90101012);
                c.fill(sw - 2, y, sw, y + 9, Gfx.ACC);
                Gfx.text(c, m.name, sw - w - 5, y + 1.5f, Gfx.TEXT, 0.8f);
                y += 9;
            }
        }
    }

    /** Gradient from the top of the screen down to the horizon line (only with open sky overhead). */
    private static void sky(DrawContext c, MinecraftClient mc, int w, int h) {
        if (mc.world == null) return;
        BlockPos pos = mc.player.getBlockPos();
        if (mc.world.getLightLevel(LightType.SKY, pos) < 12) return;

        double fov = Math.toRadians(mc.options.getFov().getValue());
        double pitchDeg = mc.player.getPitch();
        double hyD = h / 2.0 - (h / 2.0) * Math.tan(Math.toRadians(pitchDeg)) / Math.tan(fov / 2.0);
        int hy = (int) Math.max(0, Math.min(h, hyD));
        if (hy <= 0) return;

        int[] col = SKIES[Modules.skyPreset.mode];
        int a = ((int) (255 * Modules.skyStrength.num)) << 24;
        int mid = (int) (hy * 0.55);
        c.fillGradient(0, 0, w, mid, a | col[0], a | col[1]);
        c.fillGradient(0, mid, w, hy, a | col[1], a | col[2]);

        if (Modules.skyStars.bool) stars(c, w, h, hy, fov, pitchDeg, mc.player.getYaw());
        if (Modules.skyShooting.bool) shootingStar(c, w, hy);
    }

    private static void stars(DrawContext c, int w, int h, int hy, double fov, double pitchDeg, double yawDeg) {
        double f = (h / 2.0) / Math.tan(fov / 2.0);
        int n = (int) Math.min(MAX_STARS, Modules.skyCount.num);
        long t = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            double d = ((STAR_YAW[i] - yawDeg) % 360.0 + 540.0) % 360.0 - 180.0;
            double eDeg = STAR_PITCH[i] - pitchDeg;
            if (Math.abs(d) > 80 || Math.abs(eDeg) > 80) continue;
            double dr = Math.toRadians(d);
            int x = (int) (w / 2.0 + f * Math.tan(dr));
            int y = (int) (h / 2.0 + f * Math.tan(Math.toRadians(eDeg)) / Math.cos(dr));
            if (x < 0 || x >= w || y < 0 || y >= hy) continue;
            int al = (int) (150 + 100 * Math.sin(t / 400.0 + i));
            c.fill(x, y, x + 1 + (i % 7 == 0 ? 1 : 0), y + 1 + (i % 7 == 0 ? 1 : 0), (al << 24) | 0xFFFFFF);
        }
    }

    private static void shootingStar(DrawContext c, int w, int hy) {
        long t = System.currentTimeMillis();
        long slot = t / 4500;
        double ph = (t % 4500) / 800.0;
        if (ph > 1) return;
        Random r = new Random(slot * 7919L);
        int x0 = r.nextInt(Math.max(1, w));
        int y0 = r.nextInt(Math.max(1, hy / 2));
        for (int i = 0; i < 12; i++) {
            double p = ph - i * 0.02;
            if (p < 0) break;
            int x = x0 + (int) (p * 120), y = y0 + (int) (p * 50);
            if (y >= hy) break;
            int al = (int) (255 * (1 - i / 12.0) * (1 - ph * 0.5));
            c.fill(x, y, x + 2, y + 1, (al << 24) | 0xFFFFFF);
        }
    }

    private static void vignette(DrawContext c, int w, int h) {
        int e = Math.min(w, h) / 4;
        c.fillGradient(0, 0, w, e, 0xA0000000, 0x00000000);
        c.fillGradient(0, h - e, w, h, 0x00000000, 0xA0000000);
        for (int i = 0; i < e; i += 2) {
            int col = ((int) (0xA0 * (1.0 - (double) i / e))) << 24;
            c.fill(i, 0, i + 2, h, col);
            c.fill(w - i - 2, 0, w - i, h, col);
        }
    }

    private static void key(DrawContext c, String label, boolean down, int x, int y, int w) {
        Gfx.roundBorder(c, x, y, w, 16, 3, down ? Gfx.ACC_DARK : 0xC0141416, down ? Gfx.ACC : Gfx.BORDER);
        Gfx.text(c, label, x + (w - Gfx.width(label, 0.8f)) / 2f, y + 4, down ? Gfx.ACC : Gfx.TEXT, 0.8f);
    }

    private static void keystrokes(DrawContext c, MinecraftClient mc, int sh) {
        int x = 8, y = sh - 84;
        key(c, "W", mc.options.forwardKey.isPressed(), x + 18, y, 16);
        key(c, "A", mc.options.leftKey.isPressed(), x, y + 18, 16);
        key(c, "S", mc.options.backKey.isPressed(), x + 18, y + 18, 16);
        key(c, "D", mc.options.rightKey.isPressed(), x + 36, y + 18, 16);
        key(c, "SPACE", mc.options.jumpKey.isPressed(), x, y + 36, 52);
    }
}
