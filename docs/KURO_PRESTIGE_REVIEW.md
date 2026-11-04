# Kuro and Prestige engine preview review

Static review of the supplied JARs; neither client was launched or loaded. Decompiled bodies are bytecode reconstructions. Uploaded classes, assets, native libraries, and reconstructed source are not bundled in Levrn. This review informs original implementations for Minecraft 1.21.11.

## What the files contain

| Input | Evidence | Compatibility and limits |
| --- | --- | --- |
| `kuro-1.0.0.jar` | Fabric ID `kuro`, version 1.0; 3,616 archive entries, 3,220 classes; 361 reconstructed top-level client source files. | Metadata targets Minecraft `~1.21.11` / Java 21. Many classes are dependencies, helpers, or nested classes; class count is not a module count. |
| `prestige-java-1.21.1.jar` | Metadata identifies `0.3.0-engine-preview`, **Prestige Java Engine Preview (VM ONLY)**. 953 entries, 762 classes; approximately 591 reconstructed source files. Named standalone UI/engine layer around an obfuscated core. | Actual dependency is **Minecraft 1.21.1**. Additional access-widener filenames are not evidence that gameplay works on 1.21.11. Some entry points remain native. |

SHA-256:

```text
kuro-1.0.0.jar
26b937a2df31c21698af1c46948fafa6a4121694044c86f1279a6f194045a611

prestige-java-1.21.1.jar
c5f033d2203fd5a729103cc1afd4741334793fcdd3d1dfbe8c74a4e0ca16f2b6
```

The new Prestige runtime describes a catalog of **119 modules and 663 settings**. `CatalogData`, `OfflineCatalog` and `UiRuntime` expose catalog/model data; that does not establish that every native or gameplay implementation is available or working. The earlier Prestige upload exposed 121 module subclasses, which is a different measurement. The new file cannot be ranked globally above the earlier file from its version or catalog size.

## Decisions implemented in Levrn 0.3

| Area and inspected evidence | Choice and resulting behavior |
| --- | --- |
| Kuro `GlassGuiScreen`: per-control spring state, opening/closing, pane and scroll transitions. Prestige `standalone/UiScreen`: delta-time toggle easing. | Use original closed-form critically damped springs. Separate channels retain hover/toggle state across widget rebuilding; frame-rate checks compare 30 and 144 Hz. Add center-out opening/closing, page fades, selected-tab travel, eased scrolling, animated toggle knobs and hover lighting. |
| Kuro glass panels; Prestige `standalone/UiPaint`: font roles, clipping and rounded surfaces. | Original Nova interface with Rajdhani typography, cached antialiased corner coverage, moving circuit/constellation decoration and scan lighting. Header dragging persists normalized panel position. Reduced motion stops decoration and makes transitions immediate; animation speed is configurable. No third-party shader or asset is copied. |
| Kuro `setting/Setting`: bounded typed values; Prestige `standalone/SettingEditor`: numeric ranges, choices and validation. | Real mouse/keyboard numeric sliders with quantized steps, explanatory tooltips and relevant module inspectors. Debounced profile writes avoid saving on every pointer movement. Per-module bindings use Minecraft Controls. |
| Kuro `util/SwapLimit`: coordinate slot operations in a tick. | One logical inventory/slot operation per client tick across automation. A multi-click cursor transaction counts as one operation. Combat defers during that operation; panic/disconnect restoration has priority. This is coordination, not a server bypass guarantee. |
| Kuro `module/impl/AutoArmor`: upgrade worn equipment with normal inventory clicks. | Replace Levrn's empty-slot-only implementation with actual component-based armor/toughness/durability ranking, valid slot checks, configurable pacing, optional worn upgrades and Elytra preservation. Enchantment-specific optimization is not implemented. |
| Kuro `module/impl/AutoReplenish`: remember hotbar templates and refill original slots. | New Auto Replenish module: refill low/depleted stackable items into their original hotbar slots, match item components, preserve counts and avoid acting while a screen or cursor stack is active. Inventory screens update templates so intentional edits are respected. |
| Kuro `RotationEngine` and `TriggerBot`; earlier Prestige target selection. | Retain Levrn's normal-reach, visible-rotation, held-weapon cooldown behavior. Add stable eligible-target retention and facing-aware shield-breaker selection. Do not transplant historical packet schedules or arbitrary extended-reach constants. |
| Prestige `standalone/engine/EngineState`: distinguish installed/ready/callback state. | Preserve explicit Levrn initialization and attachment acknowledgement checks; sent-attack diagnostics remain distinguished from server-confirmed damage. Do not represent catalog presence or an initialization flag as proof that a feature works. |

Notifications now animate module on/off and panic actions, with a bounded queue and expiry. These are interface behavior, not an additional module-count entry. Levrn has **31 implemented modules** after Auto Replenish; this does not mean complete parity with any reference client.

## Verification boundaries

The Gradle build and pure-Java behavioral checks validate compilation, configuration, motion mathematics, target retention and action budgeting. The actual Minecraft client is used for interface inspection. An opt-in integrated-server test harness is under `src/gametest`; it is excluded from the distributable. Both cloud runs timed out while loading the world, before gameplay assertions. The second used four Java processors to bound worker threads and stalled at the same spawn-loading stage. This does not verify combat, inventory behavior, or any bypass.

No server-specific bypass, ban prevention, public-server damage delivery, or comparative performance superiority is established by static inspection. Full historical module parity, native Prestige internals and Astolfo's original Java tree remain unresolved. See [the broader parity review](INTERNAL_REVIEW.md).
