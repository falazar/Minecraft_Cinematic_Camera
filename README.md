# Camera Keyframe Pro - Technical Assessment

A lightweight Minecraft camera tool for setting up repeatable cinematic shots.
Capture named poses, play them in order over a chosen duration, and keep those
shots with the world. Video is recorded separately, for example with OBS.

## Build and try it

Requires **Minecraft 1.21.1, NeoForge 21.1.264, and Java 21**.
Run `.\gradlew.bat build` on Windows or `bash ./gradlew build` on Linux/macOS.
Close Minecraft, back up the world, then place
`build/libs/camerakeyframepro-1.0.0.jar` in the matching profile's `mods` folder.
Restart and open a single-player world.

1. `/camkey create intro`
2. Stand at the first pose and look in the desired direction: `/camkey add wide`.
3. Move and aim at a second pose: `/camkey add close`.
4. Optionally enable gentle acceleration/deceleration:
   `/camkey set_easing smoothstep`. The default is `linear`.
5. `/camkey play intro 10` saves a 10-second duration and plays the shot.
6. The final pose holds for four additional seconds, then the original player
   position, view, and game mode are restored. `/camkey stop` returns early.
7. Reopen the world and use `/camkey info intro`, then
   `/camkey usesequence intro` and `/camkey play` to repeat the saved shot.
Extra commands were added such as info, list, and delete, in order to round it out and assist in manual testing.

Hotkeys: **K** captures an automatically named pose; **Pause/Break** starts the selected
sequence. Both are rebindable in Controls. See [Details.md](Details.md) for the
full command reference, setup troubleshooting, and recovery instructions.

## Requirements and design decisions

**Key architectural decisions (why I structured it this way):** I separated
commands, camera data, storage, and playback so each part can be tested independently
and future interfaces can reuse the same operations without duplicating logic.

- **Named capture:** saves position and viewing direction in ordered sequences.
  Immutable pose, keyframe, and sequence records keep recorded instructions
  separate from live player state.
- **Timed playback:** temporarily uses the player's Spectator mode rather than
  introducing a detached camera entity. Server-side lifecycle handling restores
  state, while the client samples the path each rendered frame.
- **Interpolation:** linear position/pitch and shortest-path yaw. Optional
  whole-sequence smoothstep eases the start/end without forcing stops at every
  keyframe. Duration stays unchanged; intermediate arrival times change.
- **Persistence:** Minecraft-native NBT is authoritative; JSON is a readable
  mirror. Saves use atomic per-file replacement and report partial failures.
- **Separation:** commands parse input; `CameraController` coordinates operations;
  storage, path interpolation, and playback lifecycle have separate responsibilities.
  This allows other input adapters without duplicating command logic.
- **Command organization:** small Brigadier builder methods, such as
  `createCommand()` and `captureCommand()`, replace one deeply nested registration
  chain. This makes each command easier to find, review, and extend as the project
  grows. Keeping Minecraft's native command API also avoids another dependency
  and preserves existing syntax, autocomplete, validation, and failure results.
  Each command definition includes an example invocation.

## Known limitations / what I'd do differently with more time

Single-player host only; no multiplayer synchronization, particles, camera shake,
or video export. Time follows game ticks, so pauses and lag affect wall-clock duration.
Paths use straight segments, JSON is output-only, and crashes or power loss cannot
guarantee player-state restoration. With more time, I would prioritize validated
JSON import and a small editing GUI, then explore curved paths and expand
in-game interruption/recovery testing.

**Storage upgrade:** added easing.

## Validation and demo

On October 25, 2026, a fresh source-only copy built successfully and passed
**98 tests**, with no failures, errors, or skipped tests. Existing machine-wide
dependency caches were reused; this was not a fresh-machine or Linux/macOS test.
See [Details.md](Details.md) for the build environment and manual-test coverage.

Demo: [Camera-Keyframe-Pro-Demo.mp4](Camera-Keyframe-Pro-Demo.mp4)
(approximately **2 minutes 23 seconds**, **1080p / 30 FPS**, **41.5 MiB**).
The compressed copy preserves the full supplied recording and its audio.

## AI usage

I used GPT-6 Astra in VS Code for implementation, refactoring, tests, and
documentation. My hands-on work included setting up the command code workflow areas,
in-game testing, manually adding easing, and shifting over to reusable class boundaries. I identified that
capture/selection logic inside commands would be awkward for a future GUI or
other callers and corrected that by extracting the controller and model records.

## Future updates

- **Import JSON files:** load edited or shared sequences, with validation and
  confirmation before overwriting existing shots.
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

These are future ideas, not implemented features. For another week of work,
I would prioritize JSON import and a small editing GUI.

### Future director-shot examples

| Preset | Example |
| --- | --- |
| Establishing shot | Show a wide view of a village before moving toward the characters. |
| Close-up | Frame a character's face for a reaction. |
| Over-the-shoulder | Look past one character's shoulder at the person they are talking to. |
| Follow shot | Follow a walking character from behind at a fixed distance. |
| Orbit | Circle a character while keeping them centered. |

Example future workflow: a voice cue selects a named follow shot, a three-second
countdown gives the scene time to get ready, then playback runs with timed dialogue
subtitles. These additions are not implemented.
