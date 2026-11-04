# Levrn

An original modular Minecraft **Java Edition 1.21.11** client built with Fabric and Java 21. This repository is the first alpha foundation for the Levrn project, not a finished commercial release.

**Windows download:** [Levrn.exe](https://github.com/PeteyTB/Themanwhoclosedthedoor/raw/refs/heads/main/downloads/Levrn.exe) · [Alpha release and installation notes](https://github.com/PeteyTB/Themanwhoclosedthedoor/releases/tag/v0.2.0-alpha)

## Version 0.2: internal review and redesign

The client now has **30 implemented modules** and a rebuilt multi-category workspace with continuous scrolling, a module inspector, Nova theme, and openly licensed Rajdhani typography. Global search supports several names at once: `aura, sprint`. Selecting a category spreads its modules across the available columns. See [internal code review and complete readable module inventory](docs/INTERNAL_REVIEW.md) for what was recovered from the uploads and which features are still missing.

![Actual Minecraft workspace](docs/screenshots/workspace-window.png)

![Multiple module search in the running game](docs/screenshots/search-window.png)

**For existing users whose combat did not target players:** open Settings or the Aura/Trigger inspector and choose **PvP targeting preset**. Existing saved filters are preserved on upgrade. The preset enables player targets and disables shared scoreboard-team filtering. Combat diagnostics show filters, cooldown/alignment state and sent attacks. Attacks now use eye-to-hitbox range and visible rotation; cooldown follows the held item, including swords and axes. This fixes identifiable client-side causes; damage delivery on PvP Legacy remains unverified.

New modules: Aim Assist, Auto Weapon, Shield Breaker, Auto Totem, Auto Armor (empty slots), vanilla Auto Jump, Auto Respawn, No Bob, Stable FOV, No Hurt Cam, No Nausea, No Darkness, Combat Status, Armor HUD, Speed HUD, Ping HUD, Clock, Compass, Keystrokes, Potion HUD. New combat modules start disabled.

## LiquidBounce settings research

The official LiquidLauncher and LiquidBounce source trees were inspected. LiquidLauncher is Rust/Tauri; the actual Kotlin gameplay settings tree is in LiquidBounce. The matching **v0.37.0 / Minecraft 1.21.11** source contains 232 Module*.kt files and 1,721 directly named setting declarations, with more settings inherited or nested dynamically. See the [linked source and module/settings inventory](docs/LIQUIDBOUNCE_REVIEW.md). This is a reference inventory, not 232 modules already integrated into Levrn.

## Current implementation

- Searchable category/card interface with keyboard-accessible controls and four original themes: Nova, Dusk, Glacier, Ember.
- Trigger and nearest-target Aura with attack cooldown, visibility, team, creative, and range checks. New profiles include player targets; existing profiles preserve their filters. The Settings PvP preset can enable them. Both modules start disabled.
- Sprint, hotbar Auto Tool, toggle Zoom, and vanilla maximum Brightness. Tool, FOV, brightness, and owned sprint changes are restored when deactivated or leaving the world.
- Watermark/FPS, coordinates/dimension, active module list, and crosshair target health HUD.
- An atomic local profile with strict validation and transactional loading; a broken profile is reported without replacing the original file on startup.
- A panic key that disables every module. Module mutations run on the Minecraft client thread.

Controls: **Right Shift** opens the menu, **C** toggles zoom, **End** disables all modules. These mappings can be changed in Minecraft's Controls menu. Settings include attack range, player targeting, zoom FOV, themes, save/reload, and reset. The profile lives at `config/levrn/profile.properties` in the game directory.

## Windows executable

`build/dist/Levrn.exe` is the Windows x64 installer/attachment tool. The executable bundles the built Levrn client, official Fabric Installer 1.1.2, Fabric API 0.141.6+1.21.11, and the Java attachment agent. It discovers an existing Java 21 runtime in `JAVA_HOME`, `PATH`, the Minecraft runtime directory, or common Windows JDK directories. Java itself is not bundled. A Java 21 JDK is required for the optional attachment mode.

1. Close Minecraft and the official Minecraft Launcher.
2. Run `Levrn.exe`. First installation needs internet access for the official Fabric Loader libraries.
3. Open the Minecraft Launcher, select **Levrn 1.21.11**, and press **Play**. Normal Minecraft authentication stays with the official launcher.

The installer writes a dedicated game directory at `%APPDATA%\.minecraft\levrn\1.21.11`, adds a separate launcher profile, and backs up launcher profile files before updates. Version 0.2 migrates the earlier client JAR to a stable `mods/levrn.jar` filename, retaining a backup and preventing duplicate mod IDs; custom JVM profile settings are preserved. Existing game directories and mods are preserved. It supports both normal and Microsoft Store launcher profile files. Alternative launcher directories can be supplied with `--minecraft-dir`.

```powershell
.\Levrn.exe --java "C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe"
.\Levrn.exe --minecraft-dir "D:\Minecraft"
```

This follows the requested automatic Fabric installation option. It does not attach Fabric to an already-running vanilla game; start the installed profile to use Levrn.

### Optional attachment to an existing Fabric game

```powershell
.\Levrn.exe --list
.\Levrn.exe --attach 12345
```

Choose the PID of a Minecraft **1.21.11 / Fabric / Fabric API** instance running as your Windows user, using Java 21. The Java Attach API must be enabled in that JVM. If its launch settings disable dynamic agents, enable `-XX:+EnableDynamicAgentLoading` and restart it. The tool checks the Minecraft version and dependencies before adding Levrn's classes, initializes on the Minecraft client thread, and waits for acknowledgement. Repeated initialization is guarded. Missing dependencies and disabled agents return errors rather than reporting successful attachment. It does not install or inject Fabric into a live vanilla JVM and does not redefine Minecraft classes.

The Fabric attachment path is experimental and has not been exercised in a real game here. Automated integration tests verify clean rejection of unsupported and agent-disabled JVMs while leaving the target running. Pressing End disables modules; upgrading an attached version or unloading its classes requires restarting Minecraft.

### Rebuild the executable

Install a Java 21 JDK, Go 1.23+, and Python 3. Set `JAVA_HOME` and `LEVRN_GO` as needed. On this Linux cloud machine:

```sh
python3 scripts/gradle-cloud.py --no-daemon build
python3 scripts/package.py
python3 scripts/test-attach.py
```

`package.py` verifies pinned dependency checksums, compiles the Java controller/agent, runs Go installer tests, then builds Windows x64 and Linux test-host executables. The generated payload files stay in ignored `launcher/payload/`. The Windows executable is unsigned.

## Client development

Use a **Java 21 JDK** (a JRE alone is insufficient).

```sh
./gradlew build
./gradlew runClient
```

The intended distributable is `build/libs/levrn-0.2.0-alpha.jar`. Install Fabric Loader and Fabric API for Minecraft 1.21.11 and put the built JAR in the game instance's `mods` directory. Do not use the `-sources.jar`.

In the managed cloud environment:

```sh
bash scripts/setup-cloud.sh
python3 scripts/gradle-cloud.py runClient
```

The setup helper installs a checksum-verified JDK under `/workspace/.tools`. The Gradle helper uses inherited session proxy settings without persisting credentials. Gradle's wrapper JAR and distribution checksums were verified against Gradle's published checksums. Each cloud task is already isolated; use the existing checkout and do not create a worktree unless explicitly requested.

## Verification status

`bash scripts/test-core.sh` exercises the actual pure-Java module registry and profile implementation without Minecraft downloads. `./gradlew check` also runs these checks once Fabric dependencies resolve.

The full Minecraft 1.21.11 Gradle build now passes, including all **46 core behavioral checks**, and produces the remapped client JAR. Internet access is available; the cloud Gradle helper uses the environment's trusted Java CA store to preserve proxy TLS verification. The 1.21.11 button-rendering API mismatch and the standalone test source-set configuration were corrected during compilation.

Go installer checks pass for profile preservation, idempotent updates, malformed/conflicting input, recoverable backups, version checks, and embedded payload extraction. A real end-to-end Fabric installation ran through the Linux build in an isolated temporary Minecraft directory, installed the official libraries and both mod JARs, and preserved the existing profile. Two real JVM attachment tests pass for unsupported and agent-disabled targets.

The Windows PE executable was cross-compiled; **it has not been run on Windows**. The redesigned UI and custom font have been rendered in the actual development client on an isolated software display. Settings and multi-module search were exercised with real input. Combat, inventory automation, real-game runtime attachment, and server-specific behavior remain unverified. Complete the manual checks below before treating this alpha as a release.

## Gameplay acceptance checks (pending)

1. Launch the exact 1.21.11 development client. Open a singleplayer world; open Right Shift, search modules, switch categories and themes, resize the window, and change GUI scale.
2. Toggle Sprint and Auto Tool, mine with multiple hotbar tools, and verify the prior slot restores without overriding a manual slot change.
3. Enable/disable Zoom and Brightness, disconnect and reconnect, and verify original settings restore.
4. Enable Trigger/Aura separately and together around mobs. Verify one attack per cooldown, no attacks through walls or while menus are open, and target exclusions/range. Verify any player interactions only in an agreed test world.
5. Change settings, restart, and verify persistence. Press End and confirm every module stops. Test profile reload failures with a copy of the profile.
6. Confirm HUD elements respect F1 and visually inspect placement at small and large GUI scales.

No anti-cheat bypass or ban-prevention claim has been tested. Commercial licensing, billing, a signed updater, advanced movement/render modules, HUD dragging, per-module keybinds, and performance profiling remain future work. See [internal findings and parity](docs/INTERNAL_REVIEW.md) and [LiquidBounce settings](docs/LIQUIDBOUNCE_REVIEW.md) for source evidence and remaining work.
