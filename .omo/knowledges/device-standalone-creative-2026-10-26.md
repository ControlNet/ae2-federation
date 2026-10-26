# Standalone device materials and creative purple correction

The owner rejected missing standalone construction-block textures and identical ordinary/creative converter colours.
Creative converters should use purple; ordinary Federation devices and Quantum Cubes retain teal.

## Cause and correction

- The first integration made device JSON models empty and wrapped inventory/unformed states with miniature complete
  multiblock meshes. This did not provide proper standalone construction-block artwork. Keep native six-face cube
  JSON models and opaque 16x16 textures for items and unformed states. Only formed states get `RefinedDeviceBakedModel`.
- `tools/visual/build_device_block_textures.py` draws dedicated converter front/back/vent/top faces and a segmented
  Quantum emblem. Creative faces use purple fasteners/optics and an infinity mark. It also derives separate purple
  surface/emission atlases with the original UV layout, neutral metal colours and alpha intact.
- Formed creative geometry selects the purple atlas pair; its internal carrier uses a purple vertex consumer.
  Connected Federation cables and Quantum carriers still use the normal teal renderer.
- Occlusion, skylight transmission, light blocking and shade brightness depend on formation state. Unformed cubes
  retain normal opaque lighting; formed frames retain open lighting. Do not globally apply `noOcclusion` to these
  blocks when their unformed appearance is a solid cube.
- Inventory icon exports from a world tick need an explicit GUI model-view transform, restored afterward. The
  earlier title-screen exporter relied on its caller's transform and produced blank world-tick exports. The client
  scene now rejects empty exports; actual hotbar and first-person renders are checked separately.

## Verification

```sh
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/build_device_block_textures.py
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/validate_assets.py
./gradlew :neoforge-1.21.1:build --dependency-verification=strict --console=plain
./gradlew :neoforge-1.21.1:runRefinedDeviceClient -PrefinedDeviceOut=build/refined-devices-standalone --offline --console=plain
```

- Two new resource tests first failed against the old resources, then passed after the correction.
- Strict build: 799 tests, zero failures/errors/skips. Asset validator passed. All 11 added PNGs match bytes in the JAR.
- Client scene v4 uses a separate private review world. First run and saved-world reopen report
  `REFINED_DEVICE_SCENE_OK`. First run covers breaking/reforming the Quantum body and resource reload.
- `build/refined-devices-standalone/{first-run,reopened}/` contains placed standalone, first-person/toolbar and
  ordinary/creative formed comparisons. The initial independent icon exports there were blank due to the capture
  transform issue; use `build/refined-devices-standalone-icons/reopened/` for corrected independent item exports.
- Existing 15/15 gameplay GameTests are evidence from the preceding integration, not a new run for this correction.
- Full commands and runtime constraints: `docs/art/refined-device-runtime.md`.
