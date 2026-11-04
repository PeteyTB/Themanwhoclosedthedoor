# Internal client review and module parity

This review inspects the supplied program contents. Decompiled text is a reconstruction of compiled bytecode, not the original source or proof of current server compatibility. No supplied client code or its authentication flows were executed. Extracted code remains outside this repository under `/workspace/reference-research/internal`.

## Findings

| Input | Internal result | Limits |
| --- | --- | --- |
| Crow b1.2.9 | 292 top-level client source reconstructions, including 127 module subclasses (themes/support classes included). Reviewed KillAura, targeting, settings and compact GUI structure. | Legacy Forge/Minecraft APIs require a deliberate port. |
| Prestige 0.1.0 | 640 source reconstructions, including 121 module subclasses. Reviewed TargetSelector, KillAura, AimAssist, ClickScheduler, GUI detail pages, themes and config validation. | Complex rotation/inventory coordination is version-specific; class presence is not evidence of bypass performance. |
| Sigma5 | Reconstructed the bootstrap, followed its checksummed public prelauncher and version metadata, then downloaded and verified the full 78,057,764-byte 1.16.4 archive with 10,042 class files. | All client/game class identifiers and relevant strings are heavily obfuscated; the archive yields no plaintext module-name literals for the tested names. A broad decompile was stopped after identifying this and reconstructing a limited subset, rather than treating the output as recovered original source. |
| Astolfo executable | Read PE headers, imports, exports and sections; generated a full native disassembly outside the checkout. No Java archive or CLR metadata is present. | Stripped native machine code cannot recover the original Java module tree. Exact Astolfo feature parity is unresolved without a matching readable client JAR/source or authoritative module specification. |
| Novoline executable | Extracted the embedded ZIP/JAR and reconstructed 74 launcher classes. | Launcher/account code is not a complete game module implementation. |
| LiquidLauncher shortcut | Parsed the path, then downloaded the official LiquidLauncher and LiquidBounce source. Reviewed the exact v0.37.0 / Minecraft 1.21.11 module/settings tree. See [LiquidBounce review](LIQUIDBOUNCE_REVIEW.md). | The shortcut itself carries no client code; the gameplay tree is in the separate Kotlin client repository. |

## Public source checks

- Sigma bootstrap chain was inspected through the hostname embedded in the upload; all downloaded artifacts were checked against its published SHA-1 identifiers before analysis. The current host may serve maintained/community builds; this does not establish authenticity of the historical Sigma release.
- Read the MIT-licensed community repository `neilhuang007/sigma_buildable`, including its Jello GUI, Panel, KillAura and module registry. This older community source is a design reference, not verified Sigma 5 provenance: https://github.com/neilhuang007/sigma_buildable
- The Astolfo public site returned HTTP 403. GitHub search returned third-party lookalikes and purported cracks; those were not accepted as authoritative client source and were not incorporated.
- The attempted Future wiki module URL returned HTTP 404. The Levrn interface is an original design based on the requested visual direction, not a claimed copy of Future internals.

## What changed in Levrn

- 41 implemented modules, up from 10. See the [0.4 packet-path and module review](NETWORK_MODULE_REVIEW.md). The latest additions and selection rationale are in the [Kuro/Prestige review](KURO_PRESTIGE_REVIEW.md). No planned module is shown as a working toggle.
- Compact category lanes, continuous scrolling, category spreading, multi-module global queries (`aura, sprint`), a module inspector, Rajdhani typography (OFL) and a Nova theme.
- PvP preset and explicit filters; team filtering is optional. Hitbox distance and visible rotation precede attacks. Cooldown follows the held weapon. Diagnostics report why an attack is waiting and count sent attacks without claiming server-confirmed hits.
- New combat helpers: Aim Assist, Auto Weapon, Shield Breaker and Auto Totem. Auto Armor can upgrade worn armor using base armor, toughness and durability; it can preserve an equipped Elytra. Auto Replenish refills remembered hotbar stacks. Auto Jump uses vanilla auto jump. Auto Respawn uses the normal respawn request.
- Additional visual/HUD modules: stable FOV, no bob/hurt tilt/nausea/darkness intensity, armor durability, speed, latency, local clock, compass, keystrokes and potion effects.

## Remaining parity

Full Sigma/Astolfo/Prestige parity is **not implemented**. Advanced flight/speed/scaffold, broader inventory management, ESP/render pipelines, crystal/anchor/mace/spear sequences, historical packet exploit modules and verified server-specific behavior require separate implementation and testing. The table below records source inventory without suggesting those entries work in Levrn.

## Crow module inventory

| Class | Category | Levrn status |
| --- | --- | --- |
| `KillAura` | aura | Aura: simplified analogue |
| `Developer` | client | Not implemented |
| `FPSSpoofer` | client | Not implemented |
| `FakeHud` | client | Not implemented |
| `GuiModule` | client | Not implemented |
| `RecordingMode` | client | Not implemented |
| `SelfDestruct` | client | Not implemented |
| `Targets` | client | Not implemented |
| `Terminal` | client | Not implemented |
| `UpdateCheck` | client | Not implemented |
| `AimAssist` | combat | Aim Assist: simplified analogue |
| `AntiFireball` | combat | Not implemented |
| `AutoBlock` | combat | Not implemented |
| `AutoGHead` | combat | Not implemented |
| `AutoRod` | combat | Not implemented |
| `AutoSoup` | combat | Not implemented |
| `AutoWeapon` | combat | Auto Weapon: hotbar base attack damage |
| `Backtrack` | combat | Not implemented |
| `BlockHit` | combat | Not implemented |
| `BlockIn` | combat | Not implemented |
| `BowAimbot` | combat | Not implemented |
| `ClickAssist` | combat | Not implemented |
| `Clutch` | combat | Not implemented |
| `DelayRemover` | combat | Not implemented |
| `HitBox` | combat | Not implemented |
| `JumpReset` | combat | Not implemented |
| `KnockbackDelay` | combat | Not implemented |
| `LagKB` | combat | Not implemented |
| `LagRange` | combat | Not implemented |
| `LeftClicker` | combat | Not implemented |
| `LegitAura2` | combat | Not implemented |
| `Reach` | combat | Not implemented |
| `Velocity` | combat | Velocity: incoming entity knockback scaling |
| `WTap` | combat | W Tap: automated-attack sprint reset |
| `ConfigModule` | config | Not implemented |
| `ConfigSettings` | config | Not implemented |
| `Armour` | hotkey | Not implemented |
| `Blocks` | hotkey | Not implemented |
| `Healing` | hotkey | Not implemented |
| `Ladders` | hotkey | Not implemented |
| `Pearl` | hotkey | Not implemented |
| `Trajectories` | hotkey | Not implemented |
| `Weapon` | hotkey | Not implemented |
| `HUD` | modules | Not implemented |
| `AutoHeader` | movement | Not implemented |
| `Boost` | movement | Not implemented |
| `Fly` | movement | Not implemented |
| `InvMove` | movement | Inventory Move: physical movement keys |
| `KeepSprint` | movement | Not implemented |
| `LegitSpeed` | movement | Not implemented |
| `NoSlow` | movement | Not implemented |
| `SlyPort` | movement | Not implemented |
| `SpeedTest` | movement | Not implemented |
| `Sprint` | movement | Sprint |
| `StopMotion` | movement | Not implemented |
| `Timer` | movement | Not implemented |
| `VClip` | movement | Not implemented |
| `AutoGG` | other | Not implemented |
| `FakeChat` | other | Not implemented |
| `MiddleClick` | other | Not implemented |
| `NameHider` | other | Not implemented |
| `WaterBucket` | other | Not implemented |
| `AutoArmour` | player | Auto Armor: base armor/toughness/durability upgrades |
| `AutoJump` | player | Vanilla Auto Jump |
| `AutoPlace` | player | Not implemented |
| `AutoTool` | player | Auto Tool |
| `Blink` | player | Not implemented |
| `BlinkInfinite` | player | Not implemented |
| `Bridger` | player | Not implemented |
| `ChestStealer` | player | Not implemented |
| `FallSpeed` | player | Not implemented |
| `FastPlace` | player | Fast Place: bounded 2–4 tick block use |
| `Freecam` | player | Not implemented |
| `InvManager` | player | Not implemented |
| `NoFall` | player | Not implemented |
| `Parkour` | player | Not implemented |
| `RightClicker` | player | Not implemented |
| `SafeWalk` | player | Safe Walk: vanilla edge protection |
| `AntiShuffle` | render | Not implemented |
| `ArrayListMod` | render | Active Modules HUD |
| `BedDefense` | render | Not implemented |
| `Chams` | render | Not implemented |
| `ChestESP` | render | Not implemented |
| `Crosshair` | render | Not implemented |
| `CustomHotbar` | render | Not implemented |
| `CustomTitle` | render | Not implemented |
| `Fullbright` | render | Not implemented |
| `Indicators` | render | Not implemented |
| `Keystrokes` | render | Keystrokes HUD |
| `Logo` | render | Watermark HUD |
| `Nametags` | render | Not implemented |
| `Notifications` | render | Built-in animated module/panic notifications |
| `PenisESP` | render | Not implemented |
| `PlayerESP` | render | Not implemented |
| `Projectiles` | render | Not implemented |
| `Radar` | render | Not implemented |
| `ScoreboardMod` | render | Not implemented |
| `ServerPos` | render | Not implemented |
| `Statistics` | render | Not implemented |
| `TargetHUD` | render | Target HUD: simplified analogue |
| `Tracers` | render | Not implemented |
| `Viewmodel` | render | Not implemented |
| `ThemeAurora` | themes | Not implemented |
| `ThemeCandy` | themes | Not implemented |
| `ThemeCustom` | themes | Not implemented |
| `ThemeEmber` | themes | Not implemented |
| `ThemeGothic` | themes | Not implemented |
| `ThemeLavender` | themes | Not implemented |
| `ThemeLegacy` | themes | Not implemented |
| `ThemeMint` | themes | Not implemented |
| `ThemeModule` | themes | Not implemented |
| `ThemeNord` | themes | Not implemented |
| `ThemeOcean` | themes | Not implemented |
| `ThemePeony` | themes | Not implemented |
| `ThemePink` | themes | Not implemented |
| `ThemeRainbow` | themes | Not implemented |
| `ThemeSapphire` | themes | Not implemented |
| `ThemeShadow` | themes | Not implemented |
| `ThemeSundae` | themes | Not implemented |
| `ThemeSunkist` | themes | Not implemented |
| `ThemeSunset` | themes | Not implemented |
| `ThemeWater` | themes | Not implemented |
| `ThemeWhiteGray` | themes | Not implemented |
| `ThemeWinter` | themes | Not implemented |
| `AntiBot` | world | Not implemented |
| `ChatLogger` | world | Not implemented |
| `Scaffold` | world | Not implemented |

## Prestige module inventory

| Class | Category | Levrn status |
| --- | --- | --- |
| `AimAssist` | combat | Aim Assist: simplified analogue |
| `AnchorExploder` | combat | Not implemented |
| `AnchorMacro` | combat | Not implemented |
| `AnchorPearlCatch` | combat | Not implemented |
| `AnchorPlacer` | combat | Not implemented |
| `AntiAction` | combat | Not implemented |
| `AntiBot` | combat | Not implemented |
| `AntiWeb` | combat | Not implemented |
| `AutoAnchor` | combat | Not implemented |
| `AutoCart` | combat | Not implemented |
| `AutoCrystal` | combat | Not implemented |
| `AutoDrain` | combat | Not implemented |
| `AutoElytra` | combat | Not implemented |
| `AutoHitCrystal` | combat | Not implemented |
| `AutoObbyCrystal` | combat | Not implemented |
| `AutoSafeCart` | combat | Not implemented |
| `AutoShield` | combat | Not implemented |
| `AutoShieldBreaker` | combat | Shield Breaker: axe preference |
| `AutoTotem` | combat | Auto Totem: offhand swap below threshold |
| `Backtrack` | combat | Not implemented |
| `CartAura` | combat | Not implemented |
| `CartPearlCatch` | combat | Not implemented |
| `CobwebPlacer` | combat | Not implemented |
| `DoubleAnchor` | combat | Not implemented |
| `GhostPlace` | combat | Not implemented |
| `HitFix` | combat | Not implemented |
| `HitFlick` | combat | Not implemented |
| `Hitboxes` | combat | Not implemented |
| `InstantCrossbow` | combat | Not implemented |
| `InstantShield` | combat | Not implemented |
| `KeepSprint` | combat | Not implemented |
| `KillAura` | combat | Aura: simplified analogue |
| `NoMissDelay` | combat | Not implemented |
| `PearlFollow` | combat | Not implemented |
| `PredictDoubleHand` | combat | Not implemented |
| `SafeAnchor` | combat | Not implemented |
| `SilentAim` | combat | Not implemented |
| `SwapHit` | combat | Not implemented |
| `TotemHit` | combat | Not implemented |
| `Triggerbot` | combat | Trigger: live cooldown and crosshair checks |
| `XbowCart` | combat | Not implemented |
| `AutoMace` | mace | Not implemented |
| `AutoPearlCatch` | mace | Not implemented |
| `AutoReMace` | mace | Not implemented |
| `BreachSwap` | mace | Not implemented |
| `DiagonalPearlCatch` | mace | Not implemented |
| `ElytraWindCharge` | mace | Not implemented |
| `FastPearlCatch` | mace | Not implemented |
| `KeyWindCharge` | mace | Not implemented |
| `MacePot` | mace | Not implemented |
| `MacePrediction` | mace | Not implemented |
| `PearlWindCharge` | mace | Not implemented |
| `ShieldDrain` | mace | Not implemented |
| `ShortWindCharge` | mace | Not implemented |
| `WindChargeAura` | mace | Not implemented |
| `AntiTrap` | misc | Not implemented |
| `AutoArmor` | misc | Auto Armor: base armor/toughness/durability upgrades |
| `AutoClicker` | misc | Not implemented |
| `AutoLoot` | misc | Not implemented |
| `AutoPickaxe` | misc | Not implemented |
| `AutoPot` | misc | Not implemented |
| `AutoText` | misc | Not implemented |
| `AutoTool` | misc | Auto Tool |
| `CartRefill` | misc | Not implemented |
| `CartStealer` | misc | Not implemented |
| `ChestStealer` | misc | Not implemented |
| `Criticals` | misc | Criticals: real-jump Aura/Trigger helper |
| `CrystalOptimizer` | misc | Not implemented |
| `EnemyFaker` | misc | Not implemented |
| `FastEXP` | misc | Not implemented |
| `FastPlace` | misc | Fast Place: bounded 2–4 tick block use |
| `Friends` | misc | Not implemented |
| `KeyPearl` | misc | Not implemented |
| `MiddleClickFriend` | misc | Not implemented |
| `NameHider` | misc | Not implemented |
| `PotionRefill` | misc | Not implemented |
| `Reach` | misc | Not implemented |
| `Refill` | misc | Not implemented |
| `RightClicker` | misc | Not implemented |
| `SelfDestruct` | misc | Not implemented |
| `StreamerMode` | misc | Not implemented |
| `Teams` | misc | Not implemented |
| `AutoJumpReset` | movement | Not implemented |
| `AutoShortPearl` | movement | Not implemented |
| `FakeLag` | movement | Not implemented |
| `FastBridge` | movement | Not implemented |
| `Freecam` | movement | Not implemented |
| `NoJumpDelay` | movement | Not implemented |
| `Parkour` | movement | Not implemented |
| `ShortPearl` | movement | Not implemented |
| `Sprint` | movement | Sprint |
| `Velocity` | movement | Velocity: incoming entity knockback scaling |
| `WTap` | movement | W Tap: automated-attack sprint reset |
| `ExtendedReach` | spear | Not implemented |
| `InstantLunge` | spear | Not implemented |
| `SpearDamageIndicator` | spear | Not implemented |
| `SpearMaceDoubleTap` | spear | Not implemented |
| `Ambience` | visual | Not implemented |
| `Animations` | visual | Not implemented |
| `AntiResourcePack` | visual | Not implemented |
| `ESP` | visual | Not implemented |
| `FreeLook` | visual | Not implemented |
| `HealthIndicators` | visual | Not implemented |
| `HitEffect` | visual | Not implemented |
| `HudEditor` | visual | Not implemented |
| `ItemESP` | visual | Not implemented |
| `JumpCircles` | visual | Not implemented |
| `NameTags` | visual | Not implemented |
| `NoBounce` | visual | Not implemented |
| `OreSimulation` | visual | Not implemented |
| `PearlTrajectory` | visual | Not implemented |
| `RenderOptimizer` | visual | Not implemented |
| `ShieldRenderer` | visual | Not implemented |
| `StorageESP` | visual | Not implemented |
| `TargetRenderer` | visual | Not implemented |
| `TotemAnimation` | visual | Not implemented |
| `Trails` | visual | Not implemented |
| `Trajectories` | visual | Not implemented |
| `TwoDESP` | visual | Not implemented |
| `Weather` | visual | Not implemented |
| `XRay` | visual | Not implemented |

## Public Sigma community module inventory

These filenames come from the community source tree, not a deobfuscation of every class in the downloaded Sigma 5 archive.

- `ActiveMods`: Active Modules HUD
- `AirWalk`: Not implemented
- `AntiKnockback`: Not implemented
- `AutoArmor`: Auto Armor: base armor/toughness/durability upgrades
- `BlockFly`: Not implemented
- `BunnyHop`: Not implemented
- `CameraNoClip`: Not implemented
- `ChestStealer`: Not implemented
- `ClickGUI`: Not implemented
- `Compass`: Compass HUD
- `Fly`: Not implemented
- `HighJump`: Not implemented
- `InvCleaner`: Not implemented
- `KeyStrokes`: Keystrokes HUD
- `KillAura`: Aura: simplified analogue
- `LongJump`: Not implemented
- `MenuGUI`: Not implemented
- `MiniMap`: Not implemented
- `NoFall`: Not implemented
- `NoSlow`: Not implemented
- `Notifications`: Not implemented
- `Step`: Not implemented
- `TabGUI`: Not implemented
