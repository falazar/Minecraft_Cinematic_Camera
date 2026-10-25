# Camera Keyframe Pro - Detailed Reference

For the concise assessment handoff and quick start, see [README.md](README.md).
This document retains the full command reference and recovery instructions.

A small NeoForge tool for preparing repeatable camera shots in Minecraft.
Create a named sequence, stand at each intended camera position, look in the
desired direction, and capture a named keyframe. Your poses are saved with the
world so you can return to them later.

**Implemented:** capture, sequence selection, deletion, numbered pose info,
goto by index/name, saved playback duration, interpolated playback with player-state
restoration, optional smoothstep easing, capture/play hotkeys, and automatic NBT + JSON persistence.
This tool does not record video or world events.

## Assessment requirements

| Required capability | Implementation | Verification |
| --- | --- | --- |
| Capture a named position and rotation | `/camkey add <name>` in the selected sequence | Automated tests and in-game confirmation |
| Play a named sequence over a configurable duration | `/camkey play <sequence> <seconds>` | Automated tests; playback and normal restoration confirmed in-game |
| Interpolate between poses | Linear pose interpolation with optional whole-sequence smoothstep timing; shortest-path yaw, sampled each rendered frame | Mathematical tests; linear and smoothstep playback confirmed in-game |
| Preserve sequences across world reloads | World-local authoritative NBT plus readable JSON mirror | Automated reload tests; saved data loading confirmed after a fresh Minecraft run |

Linear interpolation meets the required scope; smoothstep is an implemented bonus.
No particles, camera shake, or multiplayer synchronization were added.

## Requirements

- Minecraft **1.21.1**, NeoForge **21.1.264**, Java **21**.
- Single-player host only. No multiplayer synchronization or dedicated-server
  capture support. The local host can use the commands without enabling cheats.
- Third-party skin packs and biome mods are optional demo extras, not dependencies.

## Build and install

From the project directory in PowerShell:

```powershell
.\gradlew.bat build
```

On Linux/macOS, use `bash ./gradlew build`; the wrapper is not currently marked
executable in Git. These platforms have not been tested locally.

This runs the automated tests and creates
`build\libs\camerakeyframepro-1.0.0.jar`.

If an old global Gradle setting points to a missing Java installation, and
`JAVA_HOME` points to Java 21:

```powershell
$javaHome = $env:JAVA_HOME.TrimEnd('\')
.\gradlew.bat "-Dorg.gradle.java.home=$javaHome" build
```

The trailing-backslash trim avoids Windows argument-quoting problems. A clean
machine with Java 21 needs no machine-specific paths in the project.

To install, close Minecraft, copy the built JAR into your matching NeoForge
profile's `mods` folder, replacing any older copy, and restart. Confirm
**Camera Keyframe Pro** appears in the Mods list.

For a separate development client, use `.\gradlew.bat runClient` (with the same
Java-home override if necessary). Its world saves/logs are under `run`, not your
launcher profile.

Gradle retains client/server development launches, JUnit tests, and mod metadata
generation. Unused template publishing, data-generation, and GameTest launch
configuration has been removed; this project currently uses JUnit rather than
registered in-game GameTests. The server launch remains available for development,
but camera operations are still limited to the local single-player host.

## Tutorial: capture and save a shot

1. Open a single-player world and type `/camkey help`.
2. Create a sequence: `/camkey create intro`. It becomes active.
3. Stand at your wide-shot position and aim the view, then `/camkey add wide`.
4. Move closer and aim again, then `/camkey add close`.
5. Run `/camkey list` to see all sequences and the active marker.
6. Run `/camkey info` to see the current sequence's two poses in capture order.
7. Save and leave the world, then reopen it and `/camkey info intro`.
   Both poses should remain, with matching coordinates and angles.
8. Select it again with `/camkey usesequence intro` before further capture.
   Selection deliberately resets on world exit.
9. To remove a pose: `/camkey deletekey close`. Re-add it if desired; new poses
   are appended, not inserted at the deleted position.

Every successful edit reports **Saved NBT + JSON**.

## Hotkeys

Under **Options > Controls > Key Binds > Camera Keyframe Pro**:

- **K - Capture keyframe:** captures into the current sequence using the first
  available name `keyframe_1`, `keyframe_2`, etc. Existing names are never overwritten.
- **Pause/Break - Play current sequence:** equivalent to `/camkey play`, using
  its saved duration. This starts playback, not a pause/resume toggle.

Both can be rebound, including to mouse buttons. Check for conflicts with other
mods; keyboards without Pause/Break can choose another key. Select/create a
sequence first. If no selection exists, the server shows the normal helpful
command error rather than capturing into an arbitrary sequence.

Only fresh key/button presses count: holding a key does not repeat actions.
Keys are ignored in chat/menus, while Minecraft is paused, outside a local world,
and during playback or its final hold. `/camkey stop` remains available in chat.
Missing duration gives the same guidance as typed playback.
After reopening a world, select a sequence again before using these keys.

## Tutorial: go to a pose and play a shot

1. Select your sequence with `/camkey usesequence intro`.
2. Run `/camkey info`. Poses are numbered from 1 in playback order.
3. `/camkey goto 1` moves you to pose 1, including viewing direction.
   `/camkey goto close` visits the pose named `close`. Your game mode is unchanged;
   choose a safe spot when using Survival, since teleporting is not collision protection.
4. Set a duration explicitly: `/camkey set_duration 10`. This saves 10 seconds
   on the current sequence in both NBT and JSON.
5. With at least two poses, `/camkey play` plays the current sequence.
   `/camkey play intro` plays a named sequence without changing selection.
   Alternatively, `/camkey play 10` or `/camkey play intro 10` validates and
   saves 10 seconds on that sequence in NBT and JSON before starting playback.
6. Playback temporarily switches you to Spectator and first-person view. It
   interpolates position and shortest-path yaw. Linear mode divides time equally
   between transitions; optional smoothstep eases the whole sequence's timing.
   Normal movement, mouse-look influence, and interaction are
   suppressed while the path controls the view.
7. After movement finishes, the final pose holds for **4 seconds**, then your
   original position, rotation, game mode, flight setting,
   flight speed, and first/third-person camera choice are restored.
8. `/camkey stop` returns immediately, including during the final hold.
   Chat and pause remain available during playback.
9. Reopen the world, select the sequence, and check `/camkey info`: the saved
   duration should remain. Old sequences have no duration until you set one.

If duration is missing and seconds are omitted, `/camkey play` does not start
and explains how to select the sequence and use `/camkey set_duration`.
There is no implicit default. Supplied seconds persist for future runs.
Playback does not start if either save fails; a JSON-only failure can still leave
the new duration saved in authoritative NBT, and chat reports that explicitly.

## Optional easing

Select a sequence, then `/camkey set_easing smoothstep` for a gentle start and
finish, or `/camkey set_easing linear` for the original timing. `/camkey info`
shows the saved choice. Both new sequences and older saved sequences default to
linear. Commands and the play hotkey use the same saved mode.

Smoothstep remaps whole-sequence progress using `t * t * (3 - 2 * t)` before
choosing the path segment. It does not force stops at intermediate keyframes,
but changes when those keyframes are reached. Position and rotation use the same
eased progress. Total movement duration and the separate four-second hold stay
unchanged. This is timing easing, not a curved path: intermediate corners and
speed changes between unequal-length segments can remain.

Changing easing saves NBT and JSON immediately and is blocked during playback.
Goto still visits the exact pose without easing. Back up the world before
upgrading to this build; see the storage-version notes below.

## Commands

Names use 1-64 lowercase letters, digits, underscores, or hyphens.
Duplicate names are rejected, never silently overwritten. Keyframe names only
need to be unique within their sequence.

| Command | Purpose |
| --- | --- |
| `/camkey help` | Show the available workflow |
| `/camkey create <name>` | Create and select a sequence in this dimension |
| `/camkey usesequence <name>` | Select a saved sequence in this dimension |
| `/camkey add [keyframe]` | Capture a named pose, or automatically choose an unused `keyframe_N` |
| `/camkey delete <sequence>` | Delete the explicitly named sequence and all its keyframes |
| `/camkey deletekey <keyframe>` | Remove one keyframe from the active sequence |
| `/camkey goto <index-or-name>` | Visit a pose in the current sequence without changing game mode |
| `/camkey set_duration <seconds>` | Save the current sequence's total duration, 0.1-3600 seconds |
| `/camkey set_easing <linear|smoothstep>` | Save the current sequence's timing mode; defaults to linear |
| `/camkey play` | Play the current sequence using its saved duration |
| `/camkey play <seconds>` | Save a duration on the current sequence, then play |
| `/camkey play <sequence> [seconds]` | Play a named sequence; supplied seconds are saved first |
| `/camkey stop` | Stop and restore original player state |
| `/camkey list [page]` | List sequences and mark the active one; defaults to page 1 |
| `/camkey info` | Show the current sequence's ordered poses, page 1 |
| `/camkey info <sequence> [page]` | Inspect a named sequence without changing selection; defaults to page 1 |
| `/camkey save` | Rewrite both formats from the current authoritative data |

Info and sequence lists show at most eight entries per page and include the next-page command.
`list` lists sequences; `info` shows keyframes. Without a sequence name, `info` uses the current
selection; if none is selected, it explains how to create/select one. To request
a later info page, supply the sequence name followed by the page number.
Tab completion suggests saved names after the first data command loads the world
data (for example, `/camkey list`). Selecting, adding, or deleting in a different
dimension is rejected; viewing lists from another dimension is allowed.
Goto also requires the sequence's dimension. Plain numeric goto targets always
mean 1-based indices, even if a keyframe has a numeric name. Indices renumber
after deletion; names remain stable. Name your poses descriptively.
For goto completion, select a sequence, type `/camkey goto front`, and press Tab
to complete a matching name such as `front_door_profile`; indices are suggested too.
A lone numeric `play` argument means seconds. If a sequence itself is named `10`,
use `/camkey play 10 5` to play that sequence for five seconds, or select it and
use bare `/camkey play` with its saved duration.
The former `createsequence` and `listsequences` names have been removed.

**Deletion changed:** `delete` now removes an entire sequence, not a keyframe.
Use `deletekey` for a single pose. Sequence deletion requires an explicit name,
the sequence's dimension, and idle playback; bare `/camkey delete` does nothing
and reports incomplete syntax. Deleting the selected sequence clears selection
without selecting a replacement. Deleting another sequence leaves selection
unchanged. There is no undo command; back up important shots first.

Coordinates are displayed with two decimal places but **saved at full double
precision**. Rounding stored coordinates is unnecessary and could alter the path.
Info rows use numbered names, parenthesized coordinates, yaw, and pitch, without
vertical-bar separators or an `XYZ` label.

## Saved files and recovery

Within each world's `data` directory:

- `camerakeyframepro.dat`: compressed NBT, **authoritative on reload**.
- `camerakeyframepro.json`: human-readable mirror, written automatically on
  each edit and `/camkey save`.

Both formats have `format_version: 3` and an ordered `sequences` array/list.
Each sequence contains `name`, `dimension`, and ordered `keyframes`; each pose
has `name`, `x`, `y`, `z`, `yaw`, and `pitch`. Positions are the player's **feet
coordinates**, not eye coordinates; angles are degrees. Yaw is normalized to
[-180, 180), and pitch is between -90 and 90. The optional sequence field
`duration_seconds` appears only after explicitly setting a duration.
Each sequence also stores `easing` as `linear` or `smoothstep`. Missing, incorrectly
typed, or unknown easing values in version 3 are rejected rather than silently
changed. Version-1 and version-2 NBT load as linear, preserving their poses and
any existing duration; version 1 still has no invented duration. The next edit
or save upgrades the data to version 3.
**Back up the world before upgrading: older mod builds cannot read version-3
data.** Restoring an older JAR alone is not a rollback of upgraded world data.

Files appear on the first edit/save. NBT and JSON are each written through a
temporary file and atomic replacement in the same directory. The filesystem
must support atomic moves. The pair is **not** a single atomic transaction:
NBT commits first, then JSON.

- If NBT saving fails, the edit is not accepted into memory and an error appears.
- If JSON fails afterward, the edit **is saved in NBT**. Chat explicitly reports
  partial success; do not repeat the edit. Fix the file/disk problem and use
  `/camkey save` to retry the mirror.
- Editing JSON does not import it. Missing/stale JSON is regenerated on the next
  successful edit or `/camkey save`, not merely by listing data.
- Corrupt/unsupported NBT blocks data commands; it is not replaced with empty
  data or recovered silently from JSON. An orphan JSON file without NBT also
  blocks initialization to prevent accidental loss.
- Back up the world before recovery. Restore a known-good NBT backup while the
  world is closed. JSON import/export commands are not implemented.
- Save failures appear in chat with details in the profile's `logs\latest.log`.
- Do not edit NBT externally while the world is open: the in-memory copy wins
  on the next edit. A 64 MiB NBT memory-accounting limit bounds file loading.

## Architecture

- `CameraPose`: immutable position and viewing direction, shared by capture,
  interpolation, goto, and restoration.
- `CameraKeyframe`: a validated name plus a `CameraPose`.
- `CameraSequence`: ordered keyframes, dimension, and optional playback duration.
- `CameraLibrary`: immutable world-level collection of sequences and edit operations.
- `CameraDataFormat`: versioned NBT encoding/decoding and equivalent JSON output.
- `CameraStorage`: world-local loading and immediate dual-format saves.
- `CameraController`: shared capture, selection, edits, goto, and playback
  coordination, including host validation and world-session ownership.
- `CameraCommands`: Brigadier parsing, controller calls, pagination, suggestions,
  and chat feedback; it does not own selection or persistence.
  Registration is a short list of per-command builder methods with usage examples,
  keeping each command easy to navigate without an additional command framework.
- `CameraPath`: pure time-based position and shortest-yaw interpolation.
- `CameraEasing`: validated linear/smoothstep timing shared by server and client
  through the sampled path.
- `CameraPlayback`: server-side movement, lifecycle guards, and restoration.
- `CameraPlaybackClient`: frame-rate sampling on the local player, input
  suppression, and first-person view restoration.
- `CameraKeyMappings` / `CameraHotkeys`: localized, rebindable client controls.
  They send the existing add/play commands for server-side validation and saving.
- `CameraKeyframeProMod`: NeoForge event registration.

Capture uses the integrated server's player state. Selection is cleared on
logout, and all cached world state is released on server stop. Minecraft's NBT
APIs are used directly instead of deferred `SavedData` writes so each edit can
report whether NBT and its JSON mirror reached disk. Playback remains separate
from command parsing and storage. The standalone records retain the existing
flat pose fields in NBT/JSON. Format 3 adds sequence easing; versions 1 and 2
remain readable, and version-1 files still load with duration unset.

The player-driven approach keeps this local tool small: it reuses Minecraft's
Spectator mode instead of introducing a detached camera entity. Immutable pose
and sequence records keep saved instructions separate from live player state.
NBT uses Minecraft's native typed storage, while JSON makes saved shots easy to
inspect without introducing a second competing source of truth.

Controller operations run on the owning server thread and do not require a
Brigadier command context. Save results distinguish complete saves from a
committed NBT file with a failed JSON mirror; callers must report that partial
failure. Playback with a supplied duration also reports a failed start after
saving, so a saved edit is not mistaken for successful playback. A future GUI
must dispatch validated requests to the server rather than invoking these
operations directly from its render thread. No GUI is implemented.

The physical client reads an immutable playback snapshot published by its own
integrated server. There are no multiplayer synchronization packets or detached
camera entities. The server moves the actual player at game-tick frequency; the
client samples the same path every rendered frame to avoid tick-sized camera
snaps. Client-only classes are registered only on the physical client.

### Playback boundaries

- Duration measures game time, so normal single-player pause pauses the shot.
  Tick lag can make it longer in real seconds. `/tick freeze` is not recommended
  for filming; freeze villager AI instead.
- Setup and return are discrete teleports, outside the interpolated shot.
  The final pose is sampled at the first tick reaching the duration, then held
  for **80 game ticks (4 seconds at normal tick speed)** before restoring player
  state. The hold is additional to the saved movement duration: a 10-second
  path returns after approximately 14 seconds. Pausing the game pauses the hold.
  Stop, logout, and interrupted-playback cleanup bypass the hold.
- Goto/play require a living player not riding, sleeping, spectating another
  entity, or carrying shoulder pets. Playback checks pose bounds/world border.
- Stand upright when capturing for consistent eye height; feet coordinates
  alone do not encode crouched/swimming poses or camera FOV.
- Scout and load the route first. Traveling into ungenerated chunks can stall
  the integrated server. Straight path segments can show corners even with
  smoothstep timing; splines are not implemented.
- Edit/select/goto/duration/easing changes and overlapping plays are rejected during
  playback. Info, lists, save, help, and stop remain available.
- Graceful logout restores state before vanilla saves the player. World shutdown,
  death notification, dimension changes, and external game-mode changes also
  stop playback. Crashes/power loss are not covered by recovery guarantees.
- Another mod can veto game-mode changes. Start is rejected if Spectator is
  blocked; failed restoration is reported rather than silently claimed successful.

## Validation and limitations

`.\gradlew.bat test` runs JUnit tests in ModDevGradle's NeoForge test environment.
Tests cover model validation, both serialized formats and version migration,
ordering, reloads, world separation, malformed data, write failures, goto,
duration validation, interpolation, tick timing, restoration, and the Brigadier workflow
using mocked game objects. These are not a substitute for the in-game tutorial
smoke test or future visual playback tests.

The last full-suite Gradle build (October 25, 2026) passed **98 tests with zero
failures, errors, or skipped tests**. These include direct controller workflows, command parsing,
hotkey event handling, partial-save recovery, version-1/2 migration, strict
version-3 validation, and smoothstep position/rotation/timing. Tests verify easing
does not force an intermediate stop, alter the final hold, or disappear on edits.
Sequence-deletion tests cover selection cleanup, explicit names, dimensions,
reload, playback lockout, and complete/partial save failures.
The preceding 83-test build was installed and hash-verified in the matching
NeoForge profile. The new 97-test easing/deletion build was installed and
hash-verified on October 25 after backing up the world and previous JAR.
The later per-command builder refactor passed command/playback regression tests
and the fresh-copy full suite described below. A subsequent clean build passed
all 98 tests and was installed into the NeoForge 1.21.1 profile on October 25.
The previous JAR was backed up, and the installed JAR's SHA-256 was verified
against the build. This includes the command refactor; the code-review suggestions
have not been implemented.

On October 25, 2026, the developer confirmed named capture, visually smooth
playback following the saved poses, and normal completion restoring the player's
position, view, and game mode in the pre-easing installed build. World reload and
deletion were confirmed in the earlier capture build, with both NBT and JSON
files present.
The developer also confirmed saved data loads successfully after a fresh
Minecraft run on October 25.
The playback hotkey (Pause/Break) was confirmed working in-game on October 25.
The developer subsequently confirmed the K capture hotkey, smoothstep playback
and its saved easing choice after reopening the world, early-stop restoration,
and the four-second final hold before normal restoration.
These are user-reported in-game checks, not conclusions drawn from mocked tests.

A fresh source-only copy, including current untracked implementation files, built
successfully with the Gradle wrapper and all 98 tests. It contained no existing
project build outputs or project cache, and Gradle build-cache reuse was disabled.
Existing machine-wide dependency and NeoForm caches were reused. The Windows
build used Java 21 with the documented Java-home override to bypass a stale
global Java setting. This verifies source-copy completeness, not a fresh-machine
or Linux/macOS build. Verify the final uploaded archive/commit contains these files.

## Future updates / if I had another week

- **Import JSON files:** load edited or shared sequences, validate their format
  and values, and confirm before overwriting existing shots. The current JSON
  mirror is output-only; editing it does not change the saved library.
- **Sequence-editing GUI:** preview poses, rename and reorder keyframes, and
  adjust duration/easing through the existing controller.
- **Undo edits and deletions:** recover from mistakes during shot setup.
- **Record character positions:** save character locations and facing directions
  separately from camera poses to help reproduce scene setups across takes.
- **Scene-start delay:** an optional countdown before playback so characters and
  recording can be ready; for example, wait three seconds before starting a shot.
- **Character tracking:** keep the camera aimed at a chosen character, or follow
  them at a chosen distance.
- **Named director-shot presets:** reusable camera setups; see the five examples below.
- **Voice integration:** trigger named shots or scene cues with voice commands.
- **Subtitles:** timed dialogue captions coordinated with scene playback.
- **Curved camera paths:** smooth direction changes between keyframes, beyond
  the existing straight segments and start/end easing.
- **More in-game regression checks:** exercise chunk-loading stalls,
  interrupted playback, and player-state restoration.

These are future improvements, not features claimed by this submission.
For another week of work, I would prioritize JSON import and a small editing GUI.

### Future director-shot examples

| Preset | Example |
| --- | --- |
| Establishing shot | Show a wide view of a village before moving toward the characters. |
| Close-up | Frame a character's face for a reaction. |
| Over-the-shoulder | Look past one character's shoulder at the person they are talking to. |
| Follow shot | Follow a walking character from behind at a fixed distance. |
| Orbit | Circle a character while keeping them centered. |

These presets, voice integration, and subtitles are not implemented. An example
future workflow is a voice cue selecting a named follow shot, a three-second
countdown, then camera playback with timed dialogue subtitles.

## AI-usage notes

I used GPT-6 Astra in VS Code for implementation, refactoring, automated tests,
and documentation. My hands-on work included setting up the command workflow,
testing the mod in Minecraft, manually adding easing, and directing the change from command-specific
operations to reusable classes. One design issue I caught in the AI-assisted
version was that capture and selection logic lived inside the command class,
making it awkward for a future GUI or another caller to reuse. We corrected
that by moving those operations into `CameraController` and separating poses,
keyframes, and sequences into their own records; the GUI itself is not implemented.

## Submission handoff status

The [README](README.md) is the main assessment handoff; this document provides
the detailed technical and usage reference. The supplied recording is included as
[Camera-Keyframe-Pro-Demo.mp4](Camera-Keyframe-Pro-Demo.mp4), compressed from
105.15 MiB to 41.49 MiB while retaining 1080p, 30 FPS, all 4,291 video frames,
and the original audio stream. Full video/audio decoding was verified successfully.
Its measured duration is approximately **2 minutes 23 seconds**; the full recording
is retained by choice. Visual content has not been independently reviewed by the
assistant. The source handoff includes the camera implementation, tests, both
documentation files, and the compressed recording.

Before sending:

- Verify the final source archive or uploaded commit includes all tested files;
  a fresh copy of the working-tree source has already passed the build and tests.
- Check that the recipient can access and play the linked demo.
- Supply the final source/repository link and [README](README.md), including its AI notes.
- Optionally include the matching built JAR and reproducible shot data. Any
  third-party demo assets require separate redistribution permission.

## Mapping names and resources

Minecraft mappings are covered by Mojang's mapping license:
[license reference](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).

- [NeoForge documentation](https://docs.neoforged.net/)
- [ModDevGradle](https://github.com/neoforged/ModDevGradle)
