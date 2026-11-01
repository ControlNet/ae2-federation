# Promo poster from guide example scenes (2026-10-26)

The user rejected the Codex promo posters (docs/gallery/overview-*.webp) as ugly and unreadable. The studio
renders of our own blocks (white Router/Switch/Interface cubes) and the night Beam Former shot did not tell
outsiders what the mod does; the teaser image did not either. The new direction: one player goal per section,
shown with the guide's example scenes (real AE2 drives, terminals, CPUs, assemblers, furnaces), plus short copy
taken from the guide pages.

## Rendering guide scenes at high resolution

GuideME renders the example SNBT structures with production models. Blender is not a practical substitute:
AE2 cable buses and drive cells are code-baked models.

tools/visual/promo/scenes.py automates all of this; the notes below explain why it works the way it does.

- It copies the guide into the output folder and adds a page `promo/scene.md` holding one
  `<GameScene zoom=".." background="#0a0f16">` with `<ImportStructure src="../assets/examples/<name>.snbt" />`
  and `<IsometricCamera yaw="195" pitch="30" />`.
- A generated init script points the `guideClient` run at its own game directory (options.txt with
  `guiScale:2`), overrides the system property `guideme.ae2.guide.sources` with the copied folder, replaces
  `programArguments` with `--width 3200 --height 2000`, and disables `prepareGuideClientOptions`.
- Xvfb `3200x2000x24 +extension GLX`; Gradle and Xvfb start in their own sessions so os.killpg stops the
  whole client afterwards.
- Pages reload live. Write the page to a temporary file and rename it into place: a partially written page
  fails to parse and the watcher may not fire again. Wait about 8 s, then `import -window root`.
- Crop by finding the exact background colour (10,15,22): its bounding box is the scene panel; then crop to
  the content with a 24 px margin.
- Render on the poster's own background colour, not a chroma key: Federation Cable is translucent and picks
  up the key colour.
- Zoom that fits a 3200x2000 window: shared_warehouse 16, order_desk 16, endpoint_furnaces 10,
  nether_outpost_overworld 11, nether_outpost_nether 16, market_hub 14, remote_assembly 12, partner_workshops 12.
- Output is deterministic: two runs gave byte-identical crops.
- BoxAnnotation outlines did not appear in these captures. The example scenes already colour each network's
  cables (purple, blue, green, yellow), so labels on the poster use matching colours instead.

## Content rules from the user

- Do not show the Beam Converter: its texture is a placeholder. scenes.py removes the
  `ae2federation:beam_converter` blocks from its copy of the structures. Never edit the shipped SNBT for this.
- The poster must show the real UIs: the topology view with its sharing-rule (policy) panel, the
  Pattern Provider to Processing Endpoint mapping view, and statistics. Use whole game windows cropped from
  docs/gallery screenshots, not tiny fragments.
- The download link is CurseForge (curseforge.com/minecraft/mc-mods/ae2-federation), not Modrinth.

Pipeline and output: promotional-overviews-2026-10-26.md. Design canvas (private artifact): claude.ai/artifact/UBoqDEv5ikhtuDzuhXxraV.
