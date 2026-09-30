# Cursor recentring when the server switches screens (2026-09-30)

## Symptom

The cursor jumped to the window centre on:

- the workspace's "Edit patterns" button, which opens AE2's Provider screen;
- the "Federation pattern mapping" button on that screen, which goes back to the workspace.

## Cause

This is vanilla behaviour, and AE2 has no fix for it.

- `ServerPlayer.openMenu` (and NeoForge's overload) calls `closeContainer()` first, which sends
  `ClientboundContainerClosePacket`.
- The client runs `setScreen(null)`. `MouseHandler.grabMouse()` moves the cursor to the centre.
- The next `setScreen(new)` calls `releaseMouse()`, which also puts it at the centre.

Order in NeoForge 21.1 `Minecraft.setScreen`:

1. `ScreenEvent.Opening`
2. `ScreenEvent.Closing(old)` / `old.removed()`
3. `releaseMouse()`
4. `init()`
5. `ScreenEvent.Init.Post`

## Fix

- `FederationScreenSwitch` (client, registered in `NeoForgeClientEntrypoint`):
  - On `Closing` of a Federation screen, it records `mouseHandler.xpos()/ypos()`, in window coordinates, so the GUI
    scale does not matter.
  - On `Init.Post` of a Federation screen within 500 ms, it calls `GLFW.glfwSetCursorPos` and sets
    `MouseHandler.xpos/ypos` through the client accessor mixin `mixin.client.MouseHandlerAccess`.
- Federation screens are `FederationGuiScale.isFixed(screen)`, or AE2's `PatternProviderScreen` of a
  `FederationPatternProviderBlockEntity`. Other screens keep vanilla behaviour.
- The pure part is `ScreenSwitchCursor` (unit test `ScreenSwitchCursorTest`). It gives the position back once, only
  within `SWITCH_MILLIS`, and drops it if a non-Federation screen opens.
- A UI test cannot show this: under xvfb the window is not active, so `grabMouse` never recentres. Check it by hand.
