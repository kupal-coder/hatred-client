# Creating a New Module — Step by Step

Every cheat/feature in Lumina is a **module**: a class extending `Element`
(`app/src/main/java/com/project/lumina/client/constructors/Element.kt`).
The fastest way to make one that feels identical to the existing modules is to
copy this recipe exactly. Canonical examples to keep open while you work:

- **Minimal logic module:** `game/module/impl/motion/SprintElement.kt` (~25 lines)
- **Full combat module:** `game/module/impl/combat/TriggerBotElement.kt`,
  `game/module/impl/combat/KillauraElement.kt`
- **Overlay module:** `game/module/impl/visual/ESPElement.kt` (+ `render/ESPRenderOverlayView.kt`),
  `game/module/impl/misc/KeyStrokes.kt` (+ `overlay/mods/KeystrokesOverlay.kt`)

## 0. How a module fits into the app (read this once)

1. All modules are instantiated once in `constructors/GameManager.kt` (`with(_elements) { add(...) }`).
2. When connected, `NetBound` (your `session`) loops `GameManager.elements` on **every
   packet** (`NetBound.kt` ~line 296): each module's `beforePacketBound()` runs in order;
   if any module calls `interceptablePacket.intercept()`, the packet is swallowed
   (never forwarded) and later modules are skipped.
3. Toggle, settings UI, ArrayList entry, and config save/load are **automatic** —
   you only write the class, its settings, and its packet logic.

## 1. Create the file

Path: `app/src/main/java/com/project/lumina/client/game/module/impl/<category>/<Name>Element.kt`

- `<category>` is the folder matching your `CheatCategory`:
  `combat | motion | world | visual | misc` (also `effect`, `game` for special cases).
- File/class naming convention: `<Name>Element`, e.g. `SprintElement`, `KillauraElement`.
- Package must match the folder: `package com.project.lumina.client.game.module.impl.motion`

## 2. Write the class header

```kotlin
package com.project.lumina.client.game.module.impl.motion

import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import com.project.lumina.client.util.AssetManager
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket

class SprintElement(
    iconResId: Int = AssetManager.getAsset("ic_run_fast_black_24dp")
) : Element(
    name = "Sprint",                        // unique, stable: config key + getModule() lookup
    category = CheatCategory.Motion,        // Combat | Motion | World | Visual | Misc | Chat | Config
    iconResId,
    displayNameResId = AssetManager.getString("module_sprint_display_name")
) {
    // settings + hooks go here
}
```

Rules for the header:

- **`name`** must be unique across all modules — `GameManager.saveConfig()` keys
  `UserConfig.json` by it, and `GameManager.getModule(name)` finds modules by it.
  Never rename it after release (users lose their saved config for it).
- **`category`** controls which tab the module appears under (`CheatCategory.kt`).
- **Icon:** `AssetManager.getAsset("<drawable-name>")` resolves a file in
  `app/src/main/res/drawable/` at runtime and **crashes if missing** — add your
  drawable first, or reuse an existing `ic_*_black_24dp` icon. (Alternative used by
  `ESPElement`: pass `displayNameResId = R.string.<name>` directly with the `R` import.)
- **`displayNameResId`:** `AssetManager.getString("module_<x>_display_name")` resolves a
  `<string name="module_<x>_display_name">` entry — you must add it to
  `app/src/main/res/values/strings.xml` (see step 5). It also **crashes if missing**.
- Optional `Element` params you rarely need: `defaultEnabled = false`,
  `private = true` (hides the module from the module list UI).

## 3. Add settings

Declare them as delegated properties using the builders from
`constructors/CheatValues.kt` (`Configurable`). Every builder auto-registers the
setting (renders in the click-GUI, persists to config) — no extra code needed.

| Builder | Example |
|---|---|
| `boolValue(name, default)` | `private var multiTarget by boolValue("Multi", false)` |
| `intValue(name, default, range)` | `private var cps by intValue("CPS", 12, 1..20)` |
| `floatValue(name, default, range)` | `private var maxRange by floatValue("Range", 15.0f, 2.0f..30.0f)` |
| `listValue(name, defaultItem, choices)` | mode selectors (`ListItem` impls) |
| `colorValue(name, defaultHex)` | `colorValue("Color", "#FF0000")` |
| `gradientValue(name, colors, type, angle)` | multi-color UI effects |

Notes:

- Setting **names must be unique within the module** — they are the JSON keys in
  `toJson()/fromJson()` (handled by `Element`, nothing to write).
- Overloads accept `@StringRes nameResId: Int` instead of a raw string for translated labels.
- Read them like normal vars (`if (multiTarget) ...`, `repeat(attackPackets) ...`).
- Look at `KillauraElement` for range/cps/packets/toggles, `ESPElement` for
  float sliders + booleans, combat modules for typical combat tuning knobs.

## 4. Add behavior — packet hooks

Override from `game/InterruptiblePacketHandler.kt` (via `Element`):

```kotlin
override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
    if (!isEnabled) return
    val packet = interceptablePacket.packet
    if (packet !is PlayerAuthInputPacket) return
    // mutate packet in place, or:
    // interceptablePacket.intercept()  // swallow it: never forwarded, later modules skipped
}

// Direction-specific variants (default = call beforePacketBound):
override fun beforeClientBound(interceptablePacket: InterceptablePacket) { ... } // relay -> game
override fun beforeServerBound(interceptablePacket: InterceptablePacket) { ... } // relay -> server

// Read-only observers (packet already forwarded, cannot intercept):
override fun afterPacketBound(packet: BedrockPacket) { ... }
override fun afterClientBound(packet: BedrockPacket) { ... }
override fun afterServerBound(packet: BedrockPacket) { ... }

// Cleanup on disconnect (reset state, hide overlays):
override fun onDisconnect(reason: String) { ... }
```

Patterns used by existing modules:

- **Per-tick logic:** gate on `packet is PlayerAuthInputPacket` (arrives every client tick).
  Killaura throttles with `packet.tick % delay` + CPS time check.
- **Mutate instead of blocking:** Sprint just adds flags —
  `packet.inputData.add(PlayerAuthInputData.SPRINTING)`. Prefer mutation; only
  `intercept()` when you must hide a packet (anti-kick/desync style).
- **Always guard with `if (!isEnabled) return`** first — hooks run for *all* modules on
  *every* packet even when disabled.
- **Guard session access with `isSessionCreated`** in `onEnabled()`/`onDisabled()` —
  the user can toggle modules before connecting, when `session` is uninitialized
  (see `TriggerBotElement.onEnabled()`).

Useful `session` (`constructors/NetBound.kt`, available as `session` inside `Element`) APIs:

- `session.localPlayer` — `.attack(entity)` (swing + `InventoryTransactionPacket`),
  `.swing()`, `.vec3Position`, `.runtimeEntityId`, `.tickExists`, `inventory`
- `session.level.entityMap` / `.playerMap` — all tracked entities; filter
  `it is Player && it !is LocalPlayer`, sort by `it.distance(session.localPlayer)`
  (see `TriggerBotElement.findTargetsInRange()`; note its `isBot()` check via blank tab-list names)
- `session.clientBound(packet)` — send a packet **to the game** (e.g. `SetEntityMotionPacket`,
  `MovePlayerPacket` for movement modules)
- `session.serverBound(packet)` — send a packet **to the server** (e.g. attack transactions)
- `session.clientBoundImmediately / serverBoundImmediately` — bypass the queue
- `session.localPlayer.distance(entity)`, `Entity.distance(...)` helpers in `game/entity/Entity.kt`

Packet classes live in `:Protocol/bedrock-codec`
(`org.cloudburstmc.protocol.bedrock.packet.*`); movement flags in
`org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData`; vectors in
`org.cloudburstmc.math.vector.Vector3f`.

## 5. Add the display-name string (and icon)

In `app/src/main/res/values/strings.xml` (alphabetical `module_*` block):

```xml
<string name="module_sprint_display_name">Sprint</string>
```

- Mirror it in `values-ja/strings.xml` and `values-zh/strings.xml` (same key, translated text).
- If you referenced a new icon in the header, add the drawable to
  `app/src/main/res/drawable/` under the exact name used in `getAsset()`.

## 6. Register the module in `GameManager`

`constructors/GameManager.kt` — **two edits, both required** (no auto-discovery;
a module that isn't added here silently doesn't exist):

1. Import: `import com.project.lumina.client.game.module.impl.motion.SprintElement`
   (keep the import block's existing grouping/order).
2. In the `with(_elements) { ... }` block (~line 95): `add(SprintElement())`
   next to modules of the same category.

That's it — the module now appears in its category tab, toggles, shows its
settings, announces toggles via `OverlayNotification`/`OverlayModuleList`, and
saves/loads with the config, all through `Element` defaults. Override
`onEnabled()`/`onDisabled()` and call `super` (which handles the ArrayList +
notification) when you need extra work like overlays — `ESPElement` and
`TriggerBotElement` do this. (Counter-example: `KeyStrokes` overrides
`onEnabled()` *without* `super`, so it gets no ArrayList entry/notification —
only skip `super` if you deliberately want that.)

## 7. (Optional) Attach an overlay / HUD

Two patterns exist — follow the nearest one:

- **Simple overlay toggle** (KeyStrokes, MiniMap, TargetHud): your module calls the
  overlay's static API in the lifecycle methods, e.g.
  `KeystrokesOverlay.setOverlayEnabled(true)` in `onEnabled()` (+ `super.onEnabled()`),
  `...setOverlayEnabled(false)` + state reset in `onDisabled()` and `onDisconnect()`.
  Overlay views live in `overlay/mods/` (`KeystrokesOverlay.kt`, `TargetHudOverlay.kt`,
  `MiniMapOverlay.kt`, ...).
- **Custom render view** (ESP): create/show a view in `onEnabled()` when
  `isSessionCreated`, dismiss it in `onDisabled()` — see `ESPElement` +
  `render/ESPRenderOverlayView.createAndShow()` / `dismissOverlay()`.
- **One-shot announcements:** `TopCenterOverlayNotification.addNotification(...)` +
  `OverlayManager.showOverlayWindow(...)` (see `TriggerBotElement.onEnabled()`).

## 8. Build and test

```bash
./gradlew :app:assembleDebug
```

- Manual on-device test against a Bedrock server is the norm (start with a
  non-protected test server, e.g. CubeCraft/Hive, per README).
- Toggle the module, change each setting, restart the app — verify the setting
  persisted (`filesDir/configs/UserConfig.json` → your module's `name` block).
- If you touch release builds: `./gradlew :app:assembleRelease` runs R8 fullMode +
  resource shrinking — classes reached only by string/reflection need `-keep` rules
  in `app/proguard-rules.pro`.

## Gotchas checklist

- [ ] Forgot `add()` in `GameManager` → module invisible (most common mistake).
- [ ] `getAsset`/`getString` name typo → runtime crash (`error(...)` on lookup miss).
- [ ] Duplicate `name` → config collisions and `getModule()` returning the wrong module.
- [ ] Missing `isEnabled` guard → your hook runs while "off".
- [ ] Touching `session` in `onEnabled()` without `isSessionCreated` → crash when toggled pre-connect.
- [ ] Heavy work per packet (allocations, `distance()` over huge maps every tick) → lag;
  throttle with tick modulo / time checks like Killaura does.
- [ ] `intercept()` stops later modules too — use only when you mean to swallow the packet.
- [ ] After-hooks (`after*`) are read-only observers; interception there does nothing.
