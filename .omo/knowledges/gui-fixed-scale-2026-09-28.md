# Federation screens use a fixed GUI scale (2026-09-28)

## Problem

- The workspace is laid out for 640x400 GUI units (`FederationDomainPolicyMenu`, preferred size).
- At the test window, 1600x960 with option scale 3, only 533x320 units exist. The layout was compressed and every
  element was drawn 1.5x larger than designed.
- A player's GUI scale option therefore changed how the design looked.

## How other mods do it

- GuideME, the guidebook library AE2 bundles (`guideme.internal.screen.IndepentScaleScreen`), scales the pose.
  - It computes an effective scale and divides every mouse coordinate by it.
  - It wraps `GuiGraphics` (`ScaledGuiGraphics`), because scissor boxes are in unscaled screen pixels.
  - `DocumentScreen.calculateEffectiveScale` moves GUI scales 1 and 3 up to 2 and 4 so the unifont stays crisp.
- A pose-scaled screen would break LDLib2's UI test driver. The driver sends element bounds, which are UI
  coordinates, straight into `Screen.mouseMoved/mouseClicked`.

## What we do

- `client/FederationGuiScale` switches the window's own GUI scale while a Federation screen is shown.
  - `ScreenEvent.Opening` sets it before the screen initialises.
  - `RenderFrameEvent.Pre` re-applies it every frame, because vanilla resets it on resize and from the options
    screen. It restores the option's scale when another screen, or no screen, is shown.
- A screen counts as a Federation screen when its `ModularUIContainerScreen` menu's `modularUI` implements
  `FederationGuiScale.Fixed`.
  - The holder builds a local `FederationModularUI` class. A local class cannot use captured locals in `super(...)`,
    so they are passed in as constructor arguments.
- `client/policy/FixedGuiScale.forFramebuffer(w, h)` is the largest integer scale whose logical viewport is at
  least 640x380. The fallback is 1. Sample results:
  - 1600x960 → 2
  - 1920x1080 → 2
  - 2560x1440 → 3
  - 3840x2160 → 5
  - 1366x768 → 2
- Mouse, tooltips, scissor, LDLib2 surfaces and the test driver all keep working, because the whole GUI coordinate
  space changes consistently.

## Tests

- T33/T15 evidence records `guiScale` as the player's option and `effectiveGuiScale` as the window's scale.
- The T33 verifier requires option scales {2,3,4} to have been exercised, and the effective scale to be {2} at
  1600x960.
- The narrow-layout checks resize the window to 320x240 physical pixels (scale 1), not 960x720 or 1280x960 with
  scale 3 or 4.

## Environment note

- T34 can fail with "server did not stop cleanly" when the VM disk stalls. `IOWorker-chunk` then sits in `pwrite`,
  and `/proc/pressure/io` shows about 20% full. This happens on the committed baseline too, so rerun once the disk
  recovers.
