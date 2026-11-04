# Levrn 0.4: packet sequencing, compact UI and module expansion

The reported kick said only “sending invalid packets.” Without a server log or a specific packet/check name, its exact cause remains unknown. This update removes avoidable packet patterns and adds controlled verification; it does not claim universal anti-cheat compatibility.

## Reference comparison

- Revisited the readable community Sigma `KillAura.java` and `Modules.java`. Its camera-independent rotation path replaces historical movement packets with rotated variants. Its legacy `C03/C05/C06` formats, ground handling and sprint packets must not be transplanted into Minecraft 1.21.11. This community source is not established as the original obfuscated Sigma 5 source.
- Revisited Crow `Velocity`, `WTap`, `SafeWalk`, `FastPlace` and inventory movement; Prestige `Criticals`, `Velocity` and `WTap`; Kuro `Criticals`, `Velocity`, `SafeWalk`, `AutoEat`, `AutoFish` and `FastPlace`.
- Cross-checked LiquidBounce **v0.37.0 / Minecraft 1.21.11**, specifically `CriticalsJump`, `VelocityModify`, `ModuleSafeWalk`, `ModuleAutoFish`, `ModuleFastPlace` and inventory movement. The Java gameplay settings are in LiquidBounce's Kotlin source, not the Rust LiquidLauncher. Server-mode names and comments are not verification that a bypass works now.
- Inspected the exact 1.21.11 vanilla bytecode for `LocalPlayer.sendPosition` and `ClientPacketListener.handleSetEntityMotion`, and the mapped signatures of the UI, input and renderer hooks. All implementation here is original; reference JAR classes and reconstructed source remain outside the repository.

## Changed packet paths

Aura no longer constructs or sends a separate movement/rotation packet before attacking. Startup hooks prepare rotation immediately before vanilla `LocalPlayer.sendPosition`; vanilla chooses and serializes its normal movement update. The attack follows that method's return, and silent mode restores the local camera immediately afterward. Visible and manual-aim modes remain available. Reach, visibility, eligibility, live weapon cooldown and hitbox alignment are still checked. Silent describes camera behavior, not invisibility to server checks.

Position and angle checks reject non-finite/out-of-world values before combat. Target-angle calculation uses `atan2` and finite checks. Joining a world starts a synchronization grace period; authoritative position corrections invalidate targeting/rotation state and pause automation for ten ticks. Teleport corrections and confirmations are never cancelled or forged.

Worn armor replacement and partial stack merging now use a cursor transaction with **one normal inventory click at a time**, separated by the inventory pacing interval. Each transaction is bound to its original player/menu and tracks its expected cursor stack. User changes or server reconciliation invalidate ownership; opening a screen or panic attempts to return only the cursor stack it owns, without dropping items. Combat and other automation pause during the transaction. Single-click swaps/quick moves retain the shared action budget.

## Ten new working implementations (41 modules total)

| Module | Implemented behavior | Scope |
| --- | --- | --- |
| Criticals | Real vanilla jump, wait for actual descent, then allow the cooldown-ready hit. | Aura/Trigger helper. No position offsets or fake airborne/ground packets. Falls back to ordinary attacks when water, flight or climbing prevents the chosen critical technique. |
| Velocity | Adjustable horizontal/vertical scaling of incoming local-player entity motion, default 0% horizontal / 100% vertical. | Entity knockback only; explosion knockback has no special mode. Server corrections remain authoritative. No invented server bypass labels. |
| W Tap | Brief sprint reset after automated attacks. | Aura/Trigger helper. No explicit sprint packet injection. |
| Safe Walk | Vanilla player edge protection without holding the user's sneak key. | Local player on the ground; not flight or fall cancellation. |
| Auto Walk | Own the forward binding while active, restore physical key state when stopped. | Pauses for screens, death, inactivity and synchronization. |
| Inventory Move | Poll actual bound movement/jump/sprint keys inside inventory/container screens. | No blink, packet delay or fabricated inventory-close packets. |
| Auto Eat | Select suitable hotbar food at configurable hunger, hold normal use, restore the prior slot. | Respects manual slot changes. Excludes poisonous, teleporting and golden foods. Does not search main inventory. |
| Auto Fish | Reel when the bobber's actual synchronized bite state is set, then recast after a delay. | Start with a manual cast; no sound-distance guess and no interaction bursts. |
| Fast Place | Bound vanilla's held-block use delay to a configurable 2–4 ticks. | Main-hand blocks while use is held; no custom placement packets. |
| No Fire Overlay | Suppress only the first-person fire renderer. | Does not alter damage or burn state. |

All ten start disabled. New and migrated profiles use silent Aura; migration expands the old default 180-degree field of view to 360, while preserving other custom limits. New settings use profile schema 4, which still reads schemas 1–3.

## Compact interface

The menu now uses a narrow sidebar and 18-unit rows instead of full-width tabs and 25-unit rows. Interface size is independent of Minecraft GUI scale (default 1.5 physical pixels per interface unit, configurable 1.0–2.0). At 1280×800 the standard panel occupies approximately 930×525 pixels, compared with about 1180×736 previously. The four-column overview packs all 41 modules into its viewport without scrolling; small windows still scroll as needed. Category views spread modules across the available columns.

Settings show interface/profile controls, with links to combat and inventory defaults. Module inspectors show relevant controls. Mouse coordinates, dragging and hit testing use the inverse interface transform. Search matches module names, IDs and categories so searching Aura does not also select helpers merely mentioning Aura in their description. The spring animations, notifications and reduced-motion preference remain.

## Installation and verification

The corrected movement, velocity and renderer paths require startup mixins. **Close Minecraft and run the installer, then launch the Levrn Fabric profile.** The attachment agent explicitly rejects late installation of this build; it cannot add startup hooks to already loaded Minecraft classes. Upgrading an already running Levrn also requires a restart.

Verification includes compilation and 82 pure-Java checks, including finite pose boundaries, critical timing, settings migration and the compact overview's complete extent. `runLocalSmoke` is an opt-in test mod using the ordinary client/server loop; its classes and resources are excluded from release JARs. The older `runClientGameTest` path remains separate because its world-loading barrier timed out in the cloud. The ordinary-loop run passed all eight scenarios:

| Check | Observed result |
| --- | --- |
| Filters | No server health loss while both target types were disabled. |
| Silent Aura | Camera remained at −180°, server golem health 100 → 72. |
| Trigger | Server health 72 → 51.86016. |
| Paced armor upgrade | Server equipped the diamond helmet; cursor empty. |
| Paced refill | Server hotbar 64 + inventory 3, conserving all 67 blocks; cursor empty. |
| Velocity | The hook processed a server motion packet; horizontal motion was scaled to zero. |
| Real-jump Criticals | Jump peak Y 5.2522 from Y 4; server health 51.86016 → 20.36016 (31.5 damage). |
| Safe Walk + Auto Walk | Server X 4.2996 / Y 4, stopped at the raised platform's edge. |

The test created an isolated flat world and stopped its client/server afterward. It used no public server. W Tap, Auto Eat, Auto Fish, Inventory Move, Fast Place and No Fire Overlay compile and have valid startup hooks where required, but were not independently exercised as gameplay scenarios in this run.

No public server, server-specific anti-cheat acceptance, Windows execution, explosion velocity mode or full Sigma/Astolfo parity is implied by these checks.
