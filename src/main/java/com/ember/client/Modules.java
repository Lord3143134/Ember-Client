package com.ember.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Registry of every module. Add new ones in init(). Put them in Category.SECRET to hide them behind the code. */
public final class Modules {
    private static final List<Module> ALL = new ArrayList<>();
    public static Module watermark, infoBar, arrayList, coords, sky, vignette, tint, keystrokes;
    public static Setting skyPreset, skyStrength, skyStars, skyCount, skyShooting, tintColor, tintStrength;

    private Modules() {}

    public static List<Module> all() { return ALL; }

    public static List<Module> of(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) {
            if (c == Category.FAVORITES ? m.favorite : m.category == c) out.add(m);
        }
        out.sort(Comparator.comparing(m -> m.name));
        return out;
    }

    /** Modules the user may currently see (secret ones only after unlocking). */
    public static boolean visible(Module m) { return !m.isSecret() || CodeLock.isUnlocked(); }

    public static void init() {
        // ---------------- HUD ----------------
        watermark = reg(new Hud("Watermark", "Client name in the corner.", true));
        infoBar = reg(new Hud("Info Bar", "Name, world type, FPS and clock at the top.", true));
        arrayList = reg(new Hud("Arraylist", "Lists the enabled modules on the right.", true));
        coords = reg(new Hud("Coordinates", "Shows your XYZ under the watermark.", true));
        keystrokes = reg(new Hud("Keystrokes", "Shows WASD and space on screen.", false));

        // ---------------- Visuals ----------------
        reg(new Module("Fullbright", "See clearly in the dark.", Category.VISUALS, true) {
            @Override public void onTick(MinecraftClient mc) {
                ClientPlayerEntity p = mc.player;
                if (p == null) return;
                StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (cur == null || cur.getDuration() < 300)
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 500, 0, false, false, false));
            }
            @Override public void onDisable(MinecraftClient mc) {
                if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        });

        reg(new Module("Zoom", "Narrows your field of view. Bind a key for quick use.", Category.VISUALS, false) {
            final Setting fov = add(Setting.num("Zoom FOV", 30, 30, 70, 1));
            int saved = -1;
            @Override public void onEnable(MinecraftClient mc) { saved = mc.options.getFov().getValue(); }
            @Override public void onTick(MinecraftClient mc) { mc.options.getFov().setValue((int) fov.num); }
            @Override public void onDisable(MinecraftClient mc) {
                if (saved > 0) mc.options.getFov().setValue(saved);
                saved = -1;
            }
        });

        sky = reg(new Module("Sky", "Paints a stylised sky from the horizon up.", Category.VISUALS, false) {
            {
                skyPreset = add(Setting.mode("Preset", "Inferno", "Dusk", "Aurora", "Void"));
                skyStrength = add(Setting.num("Strength", 0.6, 0.1, 1.0, 0.1));
                skyStars = add(Setting.bool("Stars", true));
                skyCount = add(Setting.num("Star Count", 150, 50, 400, 10));
                skyShooting = add(Setting.bool("Shooting Stars", true));
            }
        });

        vignette = reg(new Module("Vignette", "Darkens the edges of the screen.", Category.VISUALS, false) {});

        reg(tintModule());

        // ---------------- Movement ----------------
        reg(new Module("Auto Sprint", "Sprints whenever you walk forward.", Category.MOVEMENT, true) {
            @Override public void onTick(MinecraftClient mc) {
                ClientPlayerEntity p = mc.player;
                if (p == null) return;
                if (mc.options.forwardKey.isPressed() && !p.horizontalCollision && !p.isSneaking()
                        && p.getHungerManager().getFoodLevel() > 6)
                    p.setSprinting(true);
            }
        });

        // ---------------- Secret (needs the code) ----------------
        reg(new Module("Fly", "Creative style flight in survival.", Category.SECRET, true) {
            final Setting speed = add(Setting.num("Speed", 2, 1, 10, 0.5));
            @Override public void onEnable(MinecraftClient mc) { setServerFlight(mc, true); }
            @Override public void onTick(MinecraftClient mc) {
                ClientPlayerEntity p = mc.player;
                if (p == null) return;
                p.getAbilities().allowFlying = true;
                p.getAbilities().setFlySpeed((float) (0.05 * speed.num));
            }
            @Override public void onDisable(MinecraftClient mc) {
                ClientPlayerEntity p = mc.player;
                if (p != null) {
                    boolean keep = p.isCreative() || p.isSpectator();
                    p.getAbilities().allowFlying = keep;
                    if (!keep) p.getAbilities().flying = false;
                    p.getAbilities().setFlySpeed(0.05f);
                }
                setServerFlight(mc, false);
            }
        });

        reg(new Module("Speed", "Walk faster.", Category.SECRET, true) {
            final Setting mult = add(Setting.num("Multiplier", 1.5, 1, 4, 0.1));
            @Override public void onTick(MinecraftClient mc) {
                ClientPlayerEntity p = mc.player;
                if (p == null || p.hasVehicle()) return;
                float f = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
                float s = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
                if (f == 0 && s == 0) return;
                double yaw = Math.toRadians(p.getYaw());
                double dx = -Math.sin(yaw) * f + Math.cos(yaw) * s;
                double dz = Math.cos(yaw) * f + Math.sin(yaw) * s;
                double len = Math.sqrt(dx * dx + dz * dz);
                double sp = 0.2158 * mult.num;
                Vec3d v = p.getVelocity();
                p.setVelocity(dx / len * sp, v.y, dz / len * sp);
            }
        });

        reg(new Module("God Mode", "You take no damage (your own world only).", Category.SECRET, true) {
            @Override public void onEnable(MinecraftClient mc) { setInvulnerable(mc, true); }
            @Override public void onDisable(MinecraftClient mc) { setInvulnerable(mc, false); }
        });
    }

    private static <T extends Module> T reg(T m) { ALL.add(m); return m; }

    private static Module tintModule() {
        tint = new Module("Screen Tint", "Washes the whole screen in a colour.", Category.VISUALS, false) {
            {
                tintColor = add(Setting.mode("Colour", "Warm", "Cool", "Rose", "Green"));
                tintStrength = add(Setting.num("Strength", 0.2, 0.1, 0.6, 0.1));
            }
        };
        return tint;
    }

    // ---- integrated server helpers (singleplayer only) ----
    private static void setServerFlight(MinecraftClient mc, boolean on) {
        if (mc.getServer() == null || mc.player == null) return;
        var id = mc.player.getUuid();
        mc.getServer().execute(() -> {
            ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
            if (sp == null) return;
            sp.getAbilities().allowFlying = on || sp.isCreative() || sp.isSpectator();
            if (!sp.getAbilities().allowFlying) sp.getAbilities().flying = false;
            sp.sendAbilitiesUpdate();
        });
    }

    private static void setInvulnerable(MinecraftClient mc, boolean on) {
        if (mc.getServer() == null || mc.player == null) return;
        var id = mc.player.getUuid();
        mc.getServer().execute(() -> {
            ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(id);
            if (sp == null) return;
            sp.getAbilities().invulnerable = on || sp.isCreative() || sp.isSpectator();
            sp.sendAbilitiesUpdate();
        });
    }

    /** HUD pieces have no tick logic; the HUD renderer reads their `enabled` flag. */
    private static final class Hud extends Module {
        Hud(String n, String d, boolean on) { super(n, d, Category.HUD, false); enabled = on; showInHud = false; }
    }
}
