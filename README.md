Hello World smoke test
=======

This project targets Minecraft **1.21.1**, NeoForge **21.1.264**, and Java **21**.
For now, the mod only logs a startup message and greets each player when they join
a world. No camera features are implemented yet.

### Build

In PowerShell, from the project directory:

```powershell
.\gradlew.bat build
```

If Gradle reports an invalid Java home from an old global setting, and
`JAVA_HOME` points to Java 21, override it for this command:

```powershell
$javaHome = $env:JAVA_HOME.TrimEnd('\')
.\gradlew.bat "-Dorg.gradle.java.home=$javaHome" build
```

Use the same override with `runClient` in place of `build` when launching the
development client. Trimming the trailing backslash avoids Windows argument
quoting issues.

The mod JAR is written to `build\libs\camerakeyframepro-1.0.0.jar`.

### Test in Minecraft

1. Start a separate development client with `.\gradlew.bat runClient`.
   Alternatively, close Minecraft, copy the built JAR into your existing
   Minecraft 1.21.1 / NeoForge 21.1.264 profile's `mods` folder, and restart that profile.
   Replace any older copy of this mod rather than keeping duplicate JARs.
2. Confirm **Camera Keyframe Pro** appears in the Mods list.
3. Create or open a single-player world. Chat should show exactly:
   **Hello World! Camera Keyframe Pro is working.**
4. Leave and rejoin the world to see the greeting again. It should appear once
   per join, not repeatedly while playing.

The startup log should also contain
`Hello World! Camera Keyframe Pro is loaded.`
The development client's log is `run\logs\latest.log`; an installed profile's log
is in its own `logs\latest.log`. The greeting runs on the server side, so on a
multiplayer server it requires this mod to be installed on that server.

Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/
