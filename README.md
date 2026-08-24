# Ryzen Client Source

A decompiled and restructured Fabric 1.21.11 client base for RyzenClient.

## Project Structure
- src/main/java: Mod source code (dev.ryzen, org.ryzen, u.fiw.proxyserver).
- src/main/resources: Mod configuration, Mixins, UI fonts, shaders, and 3D cosmetic models.
- src/main/resources/META-INF/jars: Bundled nested dependencies (Sodium, Lithium, ViaFabricPlus, Figura, ImmediatelyFast, etc.).

## Requirements
- **Java 21 JDK** or newer
- Fabric Loader >=0.19.3
- Minecraft 1.21.11

## Building
Run with Gradle:
`ash
gradle build
`
Or import into IntelliJ IDEA / Eclipse as a Gradle project.
