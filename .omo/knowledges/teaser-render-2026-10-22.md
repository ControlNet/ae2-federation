# Teaser render from a saved world (2026-10-22)

The README teaser (`docs/teaser.webp` on dev, e8a7f564) is a GuideME scene export of a build in a player's save,
made by testmod `TeaserScene` through the `teaserClient` run (feature/function-system).

## Running it

- Copy the save to `neoforge-1.21.1/run-structure-scene/saves/<name>` (fresh copy each run, delete `session.lock`).
- `xvfb-run -a -s "-screen 0 1600x960x24" ./gradlew --no-daemon :neoforge-1.21.1:runTeaserClient
  -PteaserWorld=<name> -PteaserBox=x0,y0,z0,x1,y1,z1 -PteaserPower=x,y,z -PteaserViews=105:20`
  About 45 s. Output: `build/teaser-check/teaser-<yaw>-<pitch>.png` plus `teaser.nbt` / `teaser.snbt`.
- Run it in the foreground. Pass `--no-daemon`: an idle Gradle daemon (1.8 GB) otherwise stays behind.
- Teaser settings: yaw 105 / pitch 20 matches AE2's homepage image (north end left, west faces front, thin tops).
  WebP: Pillow `lossless=True, method=6` gives 117 KB from a 270 KB PNG with identical visible pixels; lossy q80 only
  saved 6 KB and smudged pixel edges.

## Pitfalls

- Powering: energy sharing needs ME_POWER rules, and a freshly powered hub never settled its identity. Instead the
  captured structure is "lit up": `powered`/`online` in every AE2 `visual` compound and `powered` block states set to
  true, and our cables' `Unpowered` cleared (otherwise their beams draw the red no-power alarm).
- Our cable's beam state lives only in `getUpdateTag`; merge it into each `ae2federation:*` block's structure nbt.
- `NbtUtils.structureToSnbt` packs the tag in place: pass a copy.
- `exportAsPng` writes the framebuffer's premultiplied colour as straight alpha, so glass comes out dark. The beam's
  additive glow raises alpha only by a² and nearly vanishes. `TeaserScene.straightAlpha` un-premultiplies, taking
  max(a, r, g, b) as the alpha.
- At pitch 30 a 1x1 cable's beam core sits right behind the shell's near top rail; at pitch 20 it shows through the
  glass. On a transparent or light background the beam still reads faintly: it is additive light and needs a dark
  backdrop to look as it does in game.
- `NativeImage.read(byte[])` copies through LWJGL's MemoryStack and overflows on a large PNG; read from an
  `InputStream`.
- Not causes, ruled out by pixel readback: fog, the flow mask, the frame cache. The beam is drawn into the
  off-screen target, then hidden by the rail or washed out by the alpha encoding.
- `RouterBlockEntity.getGridNode(side)` throws an NPE for a face without a port (AE2 allows null there); the tool
  avoids it, the mod bug is still open.
