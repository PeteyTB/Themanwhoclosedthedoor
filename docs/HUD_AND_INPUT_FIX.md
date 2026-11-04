# Levrn 0.4.1: supplied HUD and movement/input correction

## HUD adaptation

`FuturisticHud.java` adapts the user's pasted `dev.levrn.hud.FuturisticHud` for Minecraft 1.21.11's Mojang mappings. The existing Levrn overlay registration delegates to it; the standalone initializer from the snippet is not registered a second time.

The cyan palette, bracketed translucent panels, watermark sweep, SPD/NET/DIR/time rows, coordinate panel, warning-colored armor bars, potion timers and glowing WASD/SPACE/LMB cells follow the supplied code. Levrn's module toggles, active-module list, combat diagnostics and target panel remain available.

Every HUD label and width measurement now uses the same Rajdhani TrueType font, with a separate 4× oversampled HUD provider. The previous renderer mixed Minecraft's bitmap font with custom typography. HUD size follows the existing interface-size setting independently of Minecraft GUI scale. Text is fitted before panel dimensions are chosen; potion timers and the active list share one column, armor placement reserves space for the vanilla hotbar, and notifications stay above the lower HUD. Reduced motion stops the sweep and snaps the bars/key glows.

Keystrokes read the actual bound hardware keys rather than movement keys temporarily remapped by combat automation. The snippet's `UTC` label was changed to `LOCAL` because its clock reads `LocalTime`. Coordinate panel width includes the XYZ prefix, avoiding the snippet's possible text overflow.

The HUD renderer reads state only. It sends no packets and changes no player or module state. Its mutable data is limited to its own animation state.

## Aura + W Tap + Sprint report

The user reported another invalid-packet kick with Aura, W Tap and Sprint enabled. No packet/check name or server trace was available. Review found concrete synchronization problems in 0.4.0:

- Silent rotation was selected inside `sendPosition`, **after** movement had already been calculated using the camera's yaw.
- Sprint and W Tap directly changed sprint state without consistently updating native input.
- Criticals called the jump method late in the tick; Safe Walk overrode the ground-surface predicate without native sneak input.

Version 0.4.1 prepares combat rotation before vanilla input/physics. In silent mode it maps movement to the nearest of vanilla's eight directions in server yaw, lets vanilla calculate movement and send its normal input/movement updates, then restores the camera and interpolation angles. No additional movement packet is constructed. Because directions are quantized, the intended world direction can differ by up to 22.5°; sprinting also obeys native direction restrictions.

Sprint and W Tap now hold/release native sprint input. Criticals requests a native jump on the next tick, and Safe Walk holds native sneak near an unsupported edge. These paths use vanilla physics and serialization. Target-point block ray tracing supplements the visibility check so automatic hits do not aim at an occluded body point merely because the target's eyes are visible.

Two authoritative position corrections within 100 client ticks during active gameplay stop gameplay modules and preserve visual modules. This is a conservative stop, not a bypass. Legitimate repeated server teleports can also trigger it; gameplay modules must then be enabled again manually.

## Disconnect diagnostic

`config/levrn/last-disconnect.properties` records the actual disconnect reason, version, enabled module IDs, rotation mode, attack/correction counts and a bounded history of the last 64 outgoing packet **types**. It also records modules active when an automation stop occurred. It does not read packet payloads, chat messages, account credentials or the connection address; the server-provided disconnect reason is retained. The report stays on the user's computer and is not uploaded.

This provides evidence for a later server-specific investigation; a generic “invalid packets” message cannot establish the exact rejection check by itself.

## Verification

- 92 pure-Java checks, including native movement direction alignment, finite angles and correction-stop timing.
- The ordinary client/integrated-server scenarios pass: target filters, stationary silent Aura, Trigger, paced armor and refill, entity knockback scaling, native-jump Criticals, native-sneak edge protection/Auto Walk, **moving Aura + W Tap + Sprint**, correction-stop behavior and actual disconnect-report persistence.
- The moving combination sends native sprint input and its reset, changes world position, keeps the camera fixed, and damages the server target.
- Live captures verify Minecraft GUI scales 2 and 3, physical keystroke animations and F1 hiding. [HUD screenshot](screenshots/hud-world.png) · [GUI scale 3](screenshots/hud-gui-scale-3.png) · [motion recording](screenshots/hud-motion.mp4). These software-rendered captures are not performance benchmarks.
- Opt-in `runHudPreview` renders real armor damage, effects and all HUD panels in an isolated flat world; preview fixtures are excluded from the release JAR. `-Dlevrn.hudOnly=true` skips gameplay scenarios when only inspecting rendering.

These checks do not verify a public server's anti-cheat acceptance. The original server kick is not claimed resolved without evidence from that server. Windows execution remains untested; the executable is cross-compiled.
