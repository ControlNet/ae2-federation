# Designing in-world overlays on real captures (2026-10-13)

The bundled cable design boards (canvas rows "粗线缆 · 扳手三步选区" and "粗线缆 · 延伸、分叉、拆除、侧面连接")
show overlays that do not exist yet — selection boxes, ghost layers — on real in-game captures. The Federation
Cable's geometry is built in code (AE2 dense cable), so drawing it from textures would be neither quick nor faithful.

## Method

1. Build the scene with server commands in the visual world (`build/visual-v07`, hero world, free platform at
   x 612–634, z 886–906, floor y 68), give the probe the held item (`item replace entity VisualProbe
   weapon.mainhand with …`), `time set noon`.
2. `tp VisualProbe x feetY z yaw pitch` with yaw/pitch computed from an eye point and a target
   (`yaw = atan2(-dx, dz)`, `pitch = -atan2(dy, horizontal)`; eye = feet + 1.62). Wait ~6 s for chunks.
3. Real action bar text: `title VisualProbe actionbar {"text": …, "color": …}` just before the screenshot (it fades
   after ~3 s). Screenshot with `DISPLAY=:79 import -window root`.
4. Project world points with a pinhole camera: forward `(-sin y cos p, -sin p, cos y cos p)`, right = forward × up,
   up' = right × forward, focal = (H/2)/tan(FOV/2) at 1920×1080. **FOV is 77, not 70**: the probe flies in creative,
   and Minecraft multiplies the FOV by 1.1 while flying (fovEffectScale 1.0). With 70 every box came out too large.
5. Draw boxes in Pillow: faces warped with `Image.PERSPECTIVE` from a pure-Python homography (no numpy in the
   visual env), edges with a width of `lineWidth × focal / depth`.
6. Turn Jade's tooltip off for the capture (`config/jade/jade.json` `displayTooltip`), draw a mock in HTML where the
   design needs one, and restore the file afterwards.

Create's super glue colours used for the selection: passive `#4D9162`, highlight `#68c586`, fail `#c5b548`,
confirmed `#B5F2C6` (`SuperGlueSelectionHandler`). Its selection line width is 1/16 block; boxes are inflated
1/32.
