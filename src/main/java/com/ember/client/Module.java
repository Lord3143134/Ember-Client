package com.ember.client;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name, desc;
    public final Category category;
    /** Gameplay modules only run in singleplayer. */
    public final boolean gameplay;

    public boolean enabled, favorite, showInHud = true;
    public int key = -1;
    public final List<Setting> settings = new ArrayList<>();

    // runtime state
    boolean active, keyDown;

    protected Module(String name, String desc, Category category, boolean gameplay) {
        this.name = name; this.desc = desc; this.category = category; this.gameplay = gameplay;
    }

    protected <T extends Setting> T add(T s) { settings.add(s); return s; }

    public boolean isSecret() { return category.secret; }

    /** Whether the module is allowed to run right now. */
    public boolean allowed(boolean singleplayer) {
        if (isSecret() && !CodeLock.isUnlocked()) return false;
        return !gameplay || singleplayer;
    }

    public void onEnable(MinecraftClient mc) {}
    public void onDisable(MinecraftClient mc) {}
    public void onTick(MinecraftClient mc) {}
}
