package com.ember.client.ui;

import com.ember.client.Category;
import com.ember.client.CodeLock;
import com.ember.client.Config;
import com.ember.client.EmberClient;
import com.ember.client.Module;
import com.ember.client.Modules;
import com.ember.client.Setting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** The main menu: sidebar of categories, search bar, module list with expandable settings. */
public class ClickGui extends Screen {
    private static final int SIDE_W = 104, MOD_H = 24, SET_H = 14;

    private enum Kind { CATEGORY, MODULE, STAR, EXPAND, KEY, HUDFLAG, BOOL, MODE, NUM, CODE_ENTRY, F_HUD, F_MSG, F_LOCK }

    private record Hit(int x, int y, int w, int h, Kind kind, Module m, Setting s, Category c, boolean list) {}

    private Category cat;
    private String query = "";
    private double scroll, contentH;
    private final Set<Module> expanded = new HashSet<>();
    private final List<Hit> hits = new ArrayList<>();
    private Hit drag;
    private Module listening;
    private String notice = "";
    private long noticeUntil;
    private int lx, ly, lw, lh; // list viewport

    public ClickGui() { this(Category.MOVEMENT); }

    public ClickGui(Category start) {
        super(Text.literal("Ember"));
        this.cat = start;
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void removed() { Config.save(); }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        hits.clear();

        int pw = Math.min(440, width - 16), ph = Math.min(280, height - 16);
        int px = (width - pw) / 2, py = (height - ph) / 2;
        Gfx.roundBorder(c, px, py, pw, ph, 6, Gfx.BG, Gfx.BORDER);

        drawSidebar(c, mx, my, px, py, ph);

        int x0 = px + SIDE_W + 8, w0 = pw - SIDE_W - 16;

        // search bar
        Gfx.roundBorder(c, x0, py + 8, w0, 16, 4, Gfx.ROW, Gfx.BORDER);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (query.isEmpty()) Gfx.text(c, "Search modules...", x0 + 8, py + 13, Gfx.DIM, 0.8f);
        else Gfx.text(c, query + (blink ? "_" : ""), x0 + 8, py + 13, Gfx.TEXT, 0.8f);
        Gfx.textRight(c, "Type to search", x0 + w0 - 6, py + 14, Gfx.DIM, 0.6f);

        // list
        lx = x0; ly = py + 30; lw = w0; lh = (py + ph - 22) - ly;
        drawList(c, mx, my);

        // footer
        drawFooter(c, mx, my, x0, w0, py + ph - 17);
    }

    private void drawSidebar(DrawContext c, int mx, int my, int px, int py, int ph) {
        Gfx.round(c, px + 1, py + 1, SIDE_W, ph - 2, 5, Gfx.SIDE);
        Gfx.round(c, px + 8, py + 8, 14, 14, 4, Gfx.ACC);
        Gfx.text(c, "E", px + 13, py + 11.5f, 0xFF1A1004, 1f);
        Gfx.text(c, "Ember Client", px + 27, py + 8, Gfx.TEXT, 0.85f);
        Gfx.text(c, "Version " + EmberClient.version(), px + 27, py + 17, Gfx.DIM, 0.6f);

        int y = py + 36;
        String lastSec = "";
        for (Category k : Category.values()) {
            if (k.secret && !CodeLock.isUnlocked()) continue;
            if (!k.section.equals(lastSec)) {
                lastSec = k.section;
                if (y > py + 36) y += 4;
                Gfx.text(c, k.section, px + 10, y, Gfx.DIM, 0.6f);
                y += 9;
            }
            boolean sel = query.isEmpty() && k == cat;
            boolean hov = Gfx.in(mx, my, px + 6, y, SIDE_W - 12, 13);
            if (sel) {
                Gfx.round(c, px + 6, y, SIDE_W - 12, 13, 4, Gfx.ROW_H);
                c.fill(px + 6, y + 3, px + 8, y + 10, Gfx.ACC);
            } else if (hov) Gfx.round(c, px + 6, y, SIDE_W - 12, 13, 4, Gfx.ROW);
            Gfx.text(c, k.label, px + 14, y + 3, sel ? Gfx.TEXT : Gfx.DIM, 0.85f);
            if (k.secret) Gfx.round(c, px + SIDE_W - 16, y + 5, 4, 4, 2, Gfx.ACC);
            hits.add(new Hit(px + 6, y, SIDE_W - 12, 13, Kind.CATEGORY, null, null, k, false));
            y += 14;
        }

        // profile
        int by = py + ph - 30;
        c.fill(px + 8, by - 4, px + SIDE_W - 6, by - 3, Gfx.BORDER);
        String name = client != null && client.player != null ? client.player.getName().getString() : "player";
        Gfx.round(c, px + 8, by, 16, 16, 8, Gfx.ACC_DARK);
        Gfx.text(c, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), px + 14, by + 4, Gfx.ACC, 0.9f);
        Gfx.text(c, name, px + 29, by + 2, Gfx.TEXT, 0.75f);
        Gfx.round(c, px + 29, by + 11, 3, 3, 1, 0xFF3DD68C);
        Gfx.text(c, "Online", px + 35, by + 10, Gfx.DIM, 0.6f);
    }

    private void drawList(DrawContext c, int mx, int my) {
        String q = query.toLowerCase().trim();
        List<Module> items = new ArrayList<>();
        if (!q.isEmpty()) {
            for (Module m : Modules.all())
                if (Modules.visible(m) && (m.name.toLowerCase().contains(q) || m.desc.toLowerCase().contains(q))) items.add(m);
            items.sort(Comparator.comparing(m -> m.name));
        } else {
            for (Module m : Modules.of(cat)) if (Modules.visible(m)) items.add(m);
        }
        boolean codeEntry = !q.isEmpty() && q.length() >= 3 && "enter code".contains(q) && !CodeLock.isUnlocked();

        c.enableScissor(lx, ly, lx + lw, ly + lh);
        int top = ly - (int) scroll;
        int y = top;
        if (codeEntry) {
            boolean hov = Gfx.in(mx, my, lx, y, lw, MOD_H) && Gfx.in(mx, my, lx, ly, lw, lh);
            Gfx.round(c, lx, y, lw, MOD_H, 4, hov ? Gfx.ROW_H : Gfx.ROW);
            Gfx.text(c, "Enter Code", lx + 8, y + 5, Gfx.TEXT, 1f);
            Gfx.text(c, CodeLock.hasCode() ? "Unlock the hidden menu." : "Create a code to hide the secret menu.", lx + 8, y + 15, Gfx.DIM, 0.7f);
            Gfx.textRight(c, ">", lx + lw - 10, y + 8, Gfx.ACC, 1f);
            hits.add(new Hit(lx, y, lw, MOD_H, Kind.CODE_ENTRY, null, null, null, true));
            y += MOD_H + 3;
        }
        for (Module m : items) y = drawModule(c, m, lx, y, lw, mx, my);

        if (items.isEmpty() && !codeEntry) {
            String msg = !q.isEmpty() ? "No modules found" : cat == Category.FAVORITES ? "Star a module to pin it here" : "Nothing here yet";
            Gfx.text(c, msg, lx + lw / 2f - Gfx.width(msg, 0.8f) / 2f, ly + 20, Gfx.DIM, 0.8f);
        }
        c.disableScissor();

        contentH = y - top;
        double max = Math.max(0, contentH - lh);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    private int drawModule(DrawContext c, Module m, int x, int y, int w, int mx, int my) {
        boolean inView = y + MOD_H > ly && y < ly + lh;
        boolean hov = inView && Gfx.in(mx, my, x, y, w, MOD_H) && Gfx.in(mx, my, lx, ly, lw, lh);
        Gfx.round(c, x, y, w, MOD_H, 4, hov ? Gfx.ROW_H : Gfx.ROW);
        if (m.enabled) c.fill(x, y + 6, x + 2, y + MOD_H - 6, Gfx.ACC);

        Gfx.text(c, m.name, x + 8, y + 5, Gfx.TEXT, 1f);
        String d = m.desc;
        d = Gfx.fit(d, 0.7f, w - 90);
        Gfx.text(c, d, x + 8, y + 15, Gfx.DIM, 0.7f);

        boolean open = expanded.contains(m);
        Gfx.text(c, m.favorite ? "★" : "☆", x + w - 62, y + 7, m.favorite ? Gfx.ACC : Gfx.DIM, 1f);
        Gfx.text(c, open ? "v" : ">", x + w - 44, y + 8, Gfx.DIM, 0.8f);
        Gfx.toggle(c, x + w - 28, y + 7, m.enabled);

        hits.add(new Hit(x, y, w, MOD_H, Kind.MODULE, m, null, null, true));
        hits.add(new Hit(x + w - 66, y, 14, MOD_H, Kind.STAR, m, null, null, true));
        hits.add(new Hit(x + w - 48, y, 14, MOD_H, Kind.EXPAND, m, null, null, true));
        hits.add(new Hit(x + w - 30, y, 24, MOD_H, Kind.MODULE, m, null, null, true));
        y += MOD_H;

        if (open) {
            y += 2;
            y = settingRow(c, m, null, Kind.KEY, "Keybind", x, y, w, mx, my);
            y = settingRow(c, m, null, Kind.HUDFLAG, "Show in Arraylist", x, y, w, mx, my);
            for (Setting s : m.settings) {
                Kind k = s.type == Setting.Type.BOOL ? Kind.BOOL : s.type == Setting.Type.MODE ? Kind.MODE : Kind.NUM;
                y = settingRow(c, m, s, k, s.name, x, y, w, mx, my);
            }
            y += 2;
        }
        return y + 3;
    }

    private int settingRow(DrawContext c, Module m, Setting s, Kind k, String label, int x, int y, int w, int mx, int my) {
        Gfx.round(c, x + 4, y, w - 8, SET_H, 3, 0xFF16161A);
        Gfx.text(c, label, x + 12, y + 3.5f, Gfx.DIM, 0.8f);
        int right = x + w - 12;
        switch (k) {
            case KEY -> {
                String t = listening == m ? "..." : Gfx.keyName(m.key);
                int bw = Math.max(34, Gfx.width(t, 0.7f) + 10);
                Gfx.roundBorder(c, right - bw, y + 2, bw, 10, 3, Gfx.ROW, listening == m ? Gfx.ACC : Gfx.BORDER);
                Gfx.text(c, t, right - bw + (bw - Gfx.width(t, 0.7f)) / 2f, y + 4, Gfx.TEXT, 0.7f);
                hits.add(new Hit(right - bw, y, bw, SET_H, Kind.KEY, m, null, null, true));
            }
            case HUDFLAG -> {
                Gfx.toggle(c, right - 20, y + 2, m.showInHud);
                hits.add(new Hit(right - 24, y, 26, SET_H, Kind.HUDFLAG, m, null, null, true));
            }
            case BOOL -> {
                Gfx.toggle(c, right - 20, y + 2, s.bool);
                hits.add(new Hit(right - 24, y, 26, SET_H, Kind.BOOL, m, s, null, true));
            }
            case MODE -> {
                String t = "< " + s.modeName() + " >";
                Gfx.textRight(c, t, right, y + 4, Gfx.TEXT, 0.75f);
                int tw = Gfx.width(t, 0.75f);
                hits.add(new Hit(right - tw - 4, y, tw + 8, SET_H, Kind.MODE, m, s, null, true));
            }
            case NUM -> {
                String v = s.numText();
                Gfx.textRight(c, v, right, y + 4, Gfx.TEXT, 0.75f);
                int tw = 78, tx = right - 28 - tw;
                double f = (s.num - s.min) / (s.max - s.min);
                Gfx.round(c, tx, y + 6, tw, 3, 1, Gfx.OFF);
                Gfx.round(c, tx, y + 6, Math.max(2, (int) (tw * f)), 3, 1, Gfx.ACC);
                Gfx.round(c, tx + (int) (tw * f) - 2, y + 3, 4, 9, 2, 0xFFFFFFFF);
                hits.add(new Hit(tx - 2, y, tw + 4, SET_H, Kind.NUM, m, s, null, true));
            }
            default -> {}
        }
        return y + SET_H + 1;
    }

    private void drawFooter(DrawContext c, int mx, int my, int x0, int w0, int y) {
        Gfx.text(c, "HUD", x0, y + 3, Gfx.DIM, 0.75f);
        Gfx.toggle(c, x0 + 22, y + 1, Config.hud);
        hits.add(new Hit(x0 + 18, y, 28, 12, Kind.F_HUD, null, null, null, false));

        Gfx.text(c, "Messages", x0 + 54, y + 3, Gfx.DIM, 0.75f);
        Gfx.toggle(c, x0 + 90, y + 1, Config.toggleMessages);
        hits.add(new Hit(x0 + 86, y, 28, 12, Kind.F_MSG, null, null, null, false));

        if (CodeLock.isUnlocked()) {
            Gfx.roundBorder(c, x0 + 120, y, 28, 12, 4, Gfx.ACC_DARK, Gfx.ACC);
            Gfx.text(c, "Lock", x0 + 126, y + 3, Gfx.ACC, 0.75f);
            hits.add(new Hit(x0 + 120, y, 28, 12, Kind.F_LOCK, null, null, null, false));
        }
        boolean showNotice = System.currentTimeMillis() < noticeUntil;
        Gfx.textRight(c, showNotice ? notice : "Right click a module to expand. Type to search.", x0 + w0, y + 4,
                showNotice ? Gfx.ACC : Gfx.DIM, 0.6f);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int b = click.button();
        if (listening != null) { listening = null; return true; }
        boolean inList = Gfx.in(mx, my, lx, ly, lw, lh);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!Gfx.in(mx, my, h.x, h.y, h.w, h.h)) continue;
            if (h.list && !inList) continue;
            handle(h, mx, b);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void handle(Hit h, double mx, int button) {
        switch (h.kind) {
            case CATEGORY -> { cat = h.c; query = ""; scroll = 0; }
            case MODULE -> {
                if (button == 1) toggleExpanded(h.m);
                else if (button == 0) toggleModule(h.m);
            }
            case STAR -> { h.m.favorite = !h.m.favorite; Config.save(); }
            case EXPAND -> toggleExpanded(h.m);
            case KEY -> listening = h.m;
            case HUDFLAG -> h.m.showInHud = !h.m.showInHud;
            case BOOL -> h.s.bool = !h.s.bool;
            case MODE -> h.s.cycle(button == 1 ? -1 : 1);
            case NUM -> { drag = h; setFromMouse(h, mx); }
            case CODE_ENTRY -> client.setScreen(new CodeScreen());
            case F_HUD -> { Config.hud = !Config.hud; Config.save(); }
            case F_MSG -> { Config.toggleMessages = !Config.toggleMessages; Config.save(); }
            case F_LOCK -> {
                CodeLock.lock();
                if (cat.secret) cat = Category.MOVEMENT;
                scroll = 0;
            }
        }
    }

    private void toggleExpanded(Module m) { if (!expanded.remove(m)) expanded.add(m); }

    private void toggleModule(Module m) {
        m.enabled = !m.enabled;
        if (m.enabled && m.gameplay && !client.isInSingleplayer()) {
            notice = m.name + " only runs in singleplayer";
            noticeUntil = System.currentTimeMillis() + 2500;
        }
        Config.save();
    }

    private void setFromMouse(Hit h, double mx) {
        double f = (mx - h.x - 2) / (double) (h.w - 4);
        h.s.setNum(h.s.min + Math.max(0, Math.min(1, f)) * (h.s.max - h.s.min));
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (drag != null) { setFromMouse(drag, click.x()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (drag != null) { drag = null; Config.save(); return true; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scroll = Math.max(0, Math.min(Math.max(0, contentH - lh), scroll - vAmount * 14));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (listening != null) {
            if (k == GLFW.GLFW_KEY_BACKSPACE) listening.key = -1;
            else if (k != GLFW.GLFW_KEY_ESCAPE) listening.key = k;
            listening = null;
            Config.save();
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (!query.isEmpty()) { query = ""; scroll = 0; } else close();
            return true;
        }
        if (k == EmberClient.OPEN_KEY) { close(); return true; }
        if (k == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); scroll = 0; }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (listening != null) return true;
        int cp = input.codepoint();
        if (cp >= 32 && cp != 127 && query.length() < 32) {
            query += new String(Character.toChars(cp));
            scroll = 0;
            return true;
        }
        return super.charTyped(input);
    }
}
