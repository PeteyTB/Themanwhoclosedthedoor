# Reference analysis

This report distinguishes direct observations from design interpretations. All six uploads were inspected statically; none was executed. ZIP/JAR entry lists, metadata, class signatures, embedded string references, and small image assets were examined. `javap` read class files without loading their application classes. Extracted references and detailed inventory are retained outside the repository under `/workspace/reference-research`.

## Findings and resulting design

| Reference | What the upload actually contains | Design influence / limit |
| --- | --- | --- |
| Sigma5 | Three ZIP entries: directory, a 6,478-byte JAR, and launcher JSON. JAR is `info.sigmaclient:jellobootstrap:0.2.0`; `JelloBootstrap` supplies downloader, prelauncher, checksum, and SSL-disabling methods. JSON references the 1.16-era asset index and Java 8. | Does **not** contain the Jello GUI or full module implementations. Sigma's requested visual spirit is a creative brief, not something reconstructed from this bootstrap. Levrn uses a restrained dark surface, soft accent, short identity, and clean cards; no downloader behavior was adopted. |
| Crow b1.2.9 | 1,202 archive entries; legacy Forge/Mixin client, with compact, spacious, Crow, and KV GUI implementations. Includes category/module cards, bind/text/RGB/range/slider settings, search, configs, themes, friends, HUD elements, combat/player/world modules. | Concrete evidence for navigation, searchable cards, theme separation, and visible module state. Compact GUI signatures include detachable settings, drag state, clipped content, staged card reveal, and saved layout state. These advanced layout interactions are **not yet implemented** in Levrn. |
| Prestige 0.1.0 | 1,282 entries; manifest explicitly identifies Minecraft 1.21.11, Java 21, Fabric, Loom 1.17.21. GUI is backed by imgui-java, with a bridge to live modules/settings. UI contains ModulesPage, DetailPage, ThemesPage, ConfigsPage, KeybindsPage, WidgetsPage and HUD editor. Settings include bool, numeric range, color, enum, multi-select, keybind, text. | Concrete evidence for category/sidebar hierarchy, distinct detail pages, deliberate typography, themes, hover/toggle state, clipped text, and robust config validation. Levrn currently implements native controls, three original palettes, cards, search, and atomic transactional profiles. No claim of full visual parity; uploaded icon/font assets are not shipped. |
| LiquidLauncher.lnk | Windows Shell Link pointing at `C:\Program Files\LiquidLauncher\liquidlauncher.exe`; no LiquidBounce client or source. | Consulted the official CCBlueX/LiquidBounce repository documentation instead. Modular Fabric architecture and explicit versioning are reference principles. Its GPL source is not copied into Levrn. The shortcut cannot establish anti-cheat performance or safety. |
| astolfo.exe | Native Windows PE executable (~10 MB). Header/string inspection does not expose a readable module tree or game client source. | Retained as reference only. No claim that Levrn implements Astolfo behavior based on this upload. No authentication or executable invocation performed. |
| novoline-launcher.exe | Windows PE with an embedded ZIP/JAR: 3,229 entries, `com/novoline/launcher` and bundled dependencies; launcher identity and credential-storage classes are visible. | Confirms a launcher, not the complete game module code. No launcher authentication, credential storage, or account services were copied or invoked. |

Crow features are established by class names and signatures, not proof they perform well. Prestige's actual font resources include DM Sans/DM Serif and an Inter atlas, and the UI has a separate drawing utility with clipping and easing helpers. Levrn currently uses Minecraft's native font for compatibility; no original-client fonts were repackaged. Icons were visually inspected as reference, not used as evidence of a full GUI screenshot.

## Public source research

- Official version-specific Fabric template: https://github.com/FabricMC/fabric-example-mod/tree/1.21.11 — confirms Minecraft 1.21.11 and Fabric API `0.141.6+1.21.11`.
- Matching Fabric API interfaces: https://github.com/FabricMC/fabric/tree/1.21.11 — key-binding and HUD APIs use Mojang mappings and the modern `Identifier` name.
- Official LiquidBounce repository: https://github.com/CCBlueX/LiquidBounce/tree/nextgen — its README documents Fabric and the GPL-3.0 source license. No implementations were imported.
- A public GitHub repository search for Sigma/Jello returned community rebuilds/remakes, including `neilhuang007/sigma_buildable` and `SigmaClientReborn/Sigma-Jello-old-src-remake-Eclipse-ready`. These were **not verified as official source** and were not imported. Search results do not establish rights, authenticity, safety, or functionality.

## Input fingerprints

| Upload | Bytes | SHA-256 |
| --- | ---: | --- |
| `prestige-0.1.0.jar` | 16,082,594 | `065ff3e4eea5ef8f79523e33b91f8305fd5a79ede10d47dbeb065eeb5a59efe5` |
| `LiquidLauncher.lnk` | 1,040 | `1ffa4260488a9a80410458ceb49d1787b3c12cbe00d69edfc04ba8dbaa899488` |
| `Sigma5.zip` | 10,809 | `84b82b68720da72be2183e28d2398daae260d21b8edb3b3835092e969261b073` |
| `novoline-launcher.exe` | 5,455,278 | `1c2b041d7bc52ae46a8fe73402756b4b011c2d602e4f09a1cd6cc613bcaad97d` |
| `Crow-b1.2.9.jar` | 3,721,132 | `a56f4d355abe14c1652be568fb626a75965ada1e867fbfadc1106184a6e9f38a` |
| `astolfo.exe` | 10,029,072 | `fc019724e30980e01fc2f164c63675619e6c2a260aa1025de8be922c9911bd1e` |

## Next project milestones

1. The exact Minecraft integration now compiles and packaging/installer checks pass. Complete in-game and Windows acceptance checks; real-game attachment remains experimental.
2. Refine the original GUI against actual user-provided GUI captures, add module detail/settings panels and HUD position editing; profile frame time.
3. Add richer typed settings and module keybinds, multiple profiles, friends/target policy, notifications, and tested movement/render modules.
4. Define the commercial release model, implement entitlement flows and a signed update channel, then test packaging and upgrade/recovery behavior separately.

This is an alpha foundation. No evidence establishes superiority to the supplied clients or anti-cheat evasion.
