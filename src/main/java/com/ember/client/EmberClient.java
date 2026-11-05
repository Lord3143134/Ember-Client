package com.ember.client;

import com.ember.client.ui.ClickGui;
import com.ember.client.ui.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class EmberClient implements ClientModInitializer {
    public static final int OPEN_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private boolean openKeyDown;
    private static String version;

    /** Version from gradle.properties (mod_version), read from the built jar. */
    public static String version() {
        if (version == null)
            version = FabricLoader.getInstance().getModContainer("ember")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        return version;
    }

    @Override
    public void onInitializeClient() {
        Modules.init();
        Config.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.of("ember", "hud"),
                (ctx, tickCounter) -> HudRenderer.render(ctx));
        Runtime.getRuntime().addShutdownHook(new Thread(Config::save));
    }

    private static boolean down(MinecraftClient mc, int key) {
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private void tick(MinecraftClient mc) {
        boolean inWorld = mc.player != null && mc.world != null;
        boolean singleplayer = inWorld && mc.isInSingleplayer();

        boolean o = down(mc, OPEN_KEY);
        if (o && !openKeyDown && mc.currentScreen == null && inWorld) mc.setScreen(new ClickGui());
        openKeyDown = o;

        for (Module m : Modules.all()) {
            boolean allowed = m.allowed(singleplayer);

            if (m.key >= 0 && mc.currentScreen == null) {
                boolean d = down(mc, m.key);
                if (d && !m.keyDown && allowed && inWorld) {
                    m.enabled = !m.enabled;
                    if (Config.toggleMessages)
                        mc.player.sendMessage(Text.literal(m.name + (m.enabled ? " enabled" : " disabled")), true);
                }
                m.keyDown = d;
            }

            boolean should = m.enabled && allowed && inWorld;
            if (should && !m.active) { m.active = true; m.onEnable(mc); }
            else if (!should && m.active) { m.active = false; m.onDisable(mc); }
            if (m.active) m.onTick(mc);
        }
    }
}
