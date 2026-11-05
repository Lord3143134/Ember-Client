package com.ember.client.ui;

import com.ember.client.Category;
import com.ember.client.CodeLock;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** "Enter Code" dialog. On the very first use it asks you to create the code instead. */
public class CodeScreen extends Screen {
    private final StringBuilder code = new StringBuilder();
    private final boolean creating = !CodeLock.hasCode();
    private String error = "";
    private long errorUntil;
    private int cancelX, okX, btnY;
    private static final int BTN_W = 90, BTN_H = 18;

    public CodeScreen() { super(Text.literal("Enter Code")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int w = 250, h = 158;
        int x = (width - w) / 2, y = (height - h) / 2;
        Gfx.roundBorder(c, x, y, w, h, 6, Gfx.BG, Gfx.BORDER);

        Gfx.round(c, x + w / 2 - 9, y + 10, 18, 18, 5, Gfx.ACC);
        Gfx.text(c, "E", x + w / 2 - 3, y + 15, 0xFF1A1004, 1.1f);

        String title = creating ? "Create Code" : "Enter Code";
        Gfx.text(c, title, x + (w - Gfx.width(title, 1.3f)) / 2f, y + 34, Gfx.TEXT, 1.3f);
        String sub = creating ? "Pick a 5 character code. It unlocks the secret menu." : "Enter the code to unlock the alternate menu";
        Gfx.text(c, sub, x + (w - Gfx.width(sub, 0.7f)) / 2f, y + 49, Gfx.DIM, 0.7f);

        boolean err = System.currentTimeMillis() < errorUntil;
        int bw = 28, bh = 32, gap = 6, total = CodeLock.LENGTH * bw + (CodeLock.LENGTH - 1) * gap;
        int bx = x + (w - total) / 2, by = y + 64;
        for (int i = 0; i < CodeLock.LENGTH; i++) {
            int cx = bx + i * (bw + gap);
            int border = err ? Gfx.RED : i == code.length() ? Gfx.ACC : Gfx.BORDER;
            Gfx.roundBorder(c, cx, by, bw, bh, 5, Gfx.ROW, border);
            if (i < code.length()) {
                String ch = String.valueOf(code.charAt(i));
                Gfx.text(c, ch, cx + (bw - Gfx.width(ch, 1.6f)) / 2f, by + 10, Gfx.TEXT, 1.6f);
            } else if (i == code.length() && (System.currentTimeMillis() / 500) % 2 == 0) {
                c.fill(cx + bw / 2, by + 8, cx + bw / 2 + 1, by + bh - 8, Gfx.ACC);
            }
        }

        String hint = err ? error : "Type the code or paste it with Ctrl+V";
        Gfx.text(c, hint, x + (w - Gfx.width(hint, 0.7f)) / 2f, by + bh + 7, err ? Gfx.RED : Gfx.DIM, 0.7f);

        btnY = y + h - 30;
        cancelX = x + w / 2 - BTN_W - 5;
        okX = x + w / 2 + 5;
        Gfx.roundBorder(c, cancelX, btnY, BTN_W, BTN_H, 5, Gfx.ROW, Gfx.BORDER);
        Gfx.text(c, "Cancel", cancelX + (BTN_W - Gfx.width("Cancel", 0.85f)) / 2f, btnY + 5, Gfx.TEXT, 0.85f);

        boolean ready = code.length() == CodeLock.LENGTH;
        String okLabel = creating ? "Set code" : "Unlock";
        Gfx.roundBorder(c, okX, btnY, BTN_W, BTN_H, 5, ready ? Gfx.ACC : Gfx.ROW, ready ? Gfx.ACC : Gfx.BORDER);
        Gfx.text(c, okLabel, okX + (BTN_W - Gfx.width(okLabel, 0.85f)) / 2f, btnY + 5, ready ? 0xFF1A1004 : Gfx.DIM, 0.85f);
    }

    private void back() { client.setScreen(new ClickGui()); }

    private void submit() {
        if (code.length() != CodeLock.LENGTH) return;
        String s = code.toString();
        if (creating) {
            CodeLock.setCode(s);
            client.setScreen(new ClickGui(Category.SECRET));
        } else if (CodeLock.tryUnlock(s)) {
            client.setScreen(new ClickGui(Category.SECRET));
        } else {
            error = "Wrong code";
            errorUntil = System.currentTimeMillis() + 1500;
            code.setLength(0);
        }
    }

    private void add(String s) {
        for (char ch : s.toCharArray()) {
            if (code.length() >= CodeLock.LENGTH) break;
            if (ch < 128 && Character.isLetterOrDigit(ch)) code.append(Character.toUpperCase(ch));
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (Gfx.in(click.x(), click.y(), cancelX, btnY, BTN_W, BTN_H)) { back(); return true; }
        if (Gfx.in(click.x(), click.y(), okX, btnY, BTN_W, BTN_H)) { submit(); return true; }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_ESCAPE) { back(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        if (k == GLFW.GLFW_KEY_BACKSPACE) { if (code.length() > 0) code.setLength(code.length() - 1); return true; }
        if (input.isPaste()) { add(client.keyboard.getClipboard()); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        add(new String(Character.toChars(input.codepoint())));
        return true;
    }
}
