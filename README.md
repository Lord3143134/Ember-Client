# Ember Client (Fabric, Minecraft 1.21.11)

A singleplayer-only utility client with a dark/orange ClickGUI, a HUD, and a code-locked secret menu.

## Build
Needs Java 21.
1. Easiest: generate a fresh project at https://fabricmc.net/develop/template/ for 1.21.11 (Yarn mappings),
   then copy this project's `src/` folder over the template's `src/` (replace `fabric.mod.json`).
   Or use the build files included here and run `gradle build` (add the wrapper with `gradle wrapper`).
2. Put `build/libs/ember-client-1.0.0.jar` and Fabric API in your `.minecraft/mods` folder.

## Use
- **Right Shift** opens the menu. Left click a module = toggle, right click = expand its settings (keybind, sliders...).
- Star a module to pin it in **Favorites**. Just type to search.
- **The code:** type `code` in the search bar and click **Enter Code**. First time it asks you to *create*
  a 5-character code; after that it asks you to *enter* it. Unlocking reveals the **Secret** category
  (Fly, Speed, God Mode). It re-locks when you restart the game, or press **Lock** in the footer.
- Only a salted hash of the code is saved (`config/ember.json`). Forgot it? Delete `salt` and `codeHash` from that file.
  This hides the menu, it is not real security: anyone can edit the config or read the jar.

## Singleplayer only
Gameplay modules (Fullbright, Auto Sprint, Fly, Speed, God Mode) refuse to run unless you're in a
singleplayer world, and switch themselves off when you leave one.

## Add your own module
In `Modules.init()`: `reg(new Module("Name", "Description", Category.SECRET, true) { ... });`
Use `Category.SECRET` to hide it behind the code.

## If it fails to compile
I couldn't compile against Minecraft where I built this, so expect small API name fixes. The likeliest spots:
`PlayerAbilities` fields in `Modules.java` (Fly / God Mode), `CharInput.codepoint()` in the two screens,
and `HudElementRegistry` / `VanillaHudElements.CHAT` in `EmberClient.java`.

## Sky
Visuals > **Sky** paints a stylised gradient from the horizon up (presets: Inferno, Dusk, Aurora, Void; Strength slider).
It is a screen overlay, not a replaced sky: clouds, sun and terrain above the horizon are tinted, not removed.
It only draws when you have open sky overhead.

## More visuals
- Sky also has **Stars** (count slider) and **Shooting Stars**.
- Visuals > **Vignette** darkens the screen edges; **Screen Tint** washes the screen in Warm / Cool / Rose / Green.
- Hud > **Keystrokes** shows WASD + space (off by default).
