# Third-party materials

- `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are Gradle wrapper components from Gradle v9.6.1, licensed under Apache-2.0: https://github.com/gradle/gradle/blob/v9.6.1/LICENSE
- The Windows executable embeds the official Fabric Installer 1.1.2 and Fabric API 0.141.6+1.21.11 JARs. Fabric API's license file is retained inside its JAR; the installer's Apache-2.0 license is bundled separately. Both projects use Apache-2.0: https://github.com/FabricMC/fabric-installer/blob/master/LICENSE and https://github.com/FabricMC/fabric/blob/1.21.11/LICENSE . The installer downloads Fabric Loader and its dependencies from their official repositories.
- The executable includes the Go runtime (BSD-3-Clause): https://go.dev/LICENSE . A copy of its complete license is embedded in the package.
- Loom, Minecraft, and Java remain external build/runtime dependencies with their own licenses. Minecraft game files and Java runtimes are not bundled into Levrn.exe.
- No code, fonts, logos, textures, binaries, or decompiled implementations from the uploaded clients are embedded in Levrn. The uploads remain outside this checkout as reference material.
- LiquidBounce's public documentation identifies its source as GPL-3.0. No LiquidBounce source has been copied into this project.
- The interface uses Rajdhani Medium from Google Fonts under the SIL Open Font License 1.1. The full license is retained in `src/main/resources/META-INF/licenses/rajdhani-ofl.txt` and the client JAR. Source: https://github.com/google/fonts/tree/main/ofl/rajdhani . No font from the uploaded clients is repackaged.
