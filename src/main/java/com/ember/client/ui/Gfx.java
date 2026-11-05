package com.ember.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Small drawing helpers + the colour palette (dark panels, orange accent). */
public final class Gfx {
    public static final int BG = 0xF00F0F11, SIDE = 0xFF141416, ROW = 0xFF1A1A1E, ROW_H = 0xFF222227,
            BORDER = 0xFF2B2B31, TEXT = 0xFFEDEDF0, DIM = 0xFF8B8B95, ACC = 0xFFFF8A1F,
            ACC_DARK = 0xFF3A2410, OFF = 0xFF3A3A42, RED = 0xFFE5484D;

    private Gfx() {}

    public static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Filled rectangle with rounded corners (radius r, in GUI pixels). */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { c.fill(x, y, x + w, y + h, col); return; }
        c.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    public static void roundBorder(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        round(c, x, y, w, h, r, border);
        round(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void text(DrawContext c, String s, float x, float y, int col, float scale) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        c.drawText(tr(), s, 0, 0, col, false);
        m.popMatrix();
    }

    public static int width(String s, float scale) { return (int) Math.ceil(tr().getWidth(s) * scale); }

    public static void textRight(DrawContext c, String s, int right, float y, int col, float scale) {
        text(c, s, right - width(s, scale), y, col, scale);
    }

    /** Shortens s with "..." until it fits maxW. Always terminates (the old loop grew the string forever). */
    public static String fit(String s, float scale, int maxW) {
        if (width(s, scale) <= maxW) return s;
        int n = s.length();
        while (n > 0 && width(s.substring(0, n).trim() + "...", scale) > maxW) n--;
        return s.substring(0, n).trim() + "...";
    }

    /** 20x10 pill switch. */
    public static void toggle(DrawContext c, int x, int y, boolean on) {
        round(c, x, y, 20, 10, 5, on ? ACC : OFF);
        round(c, on ? x + 11 : x + 1, y + 1, 8, 8, 4, 0xFFFFFFFF);
    }

    public static String keyName(int key) {
        if (key < 0) return "NONE";
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-SHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-CTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R-ALT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> {
                String n = GLFW.glfwGetKeyName(key, 0);
                if (n != null) yield n.toUpperCase();
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                yield "KEY " + key;
            }
        };
    }
}
