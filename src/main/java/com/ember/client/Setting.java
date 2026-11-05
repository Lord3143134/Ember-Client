package com.ember.client;

/** One option on a module: on/off, a number slider, or a list of modes. */
public class Setting {
    public enum Type { BOOL, NUM, MODE }

    public final String name;
    public final Type type;
    public boolean bool;
    public double num, min, max, step;
    public String[] modes;
    public int mode;

    private Setting(String name, Type type) { this.name = name; this.type = type; }

    public static Setting bool(String name, boolean def) {
        Setting s = new Setting(name, Type.BOOL);
        s.bool = def;
        return s;
    }

    public static Setting num(String name, double def, double min, double max, double step) {
        Setting s = new Setting(name, Type.NUM);
        s.num = def; s.min = min; s.max = max; s.step = step;
        return s;
    }

    public static Setting mode(String name, String... modes) {
        Setting s = new Setting(name, Type.MODE);
        s.modes = modes;
        return s;
    }

    public void setNum(double v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0) v = Math.round(v / step) * step;
        num = v;
    }

    public void cycle(int dir) {
        mode = Math.floorMod(mode + dir, modes.length);
    }

    public String modeName() { return modes[mode]; }

    public String numText() {
        return step >= 1 ? Integer.toString((int) num) : String.format("%.1f", num);
    }
}
