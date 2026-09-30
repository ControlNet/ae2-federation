# Default cable flow renderer

- The user requested automatic cable flow rendering with no preview command.
- `CableFlowRenderer` now always renders loaded cable block entities with the existing standard emissive translucent shader. The mutable toggle, JVM property, client command, and command event registration were removed.
- `CableBakedModel` always suppresses the old cutout core for world rendering, while retaining the existing item behavior and baked enclosure. This is the same rendering path previously selected by the preview command.
- Elbow and junction geometry, texture, animation, view distance, and render type are unchanged.
- Published `v0.0.1` remains the earlier opt-in artifact. This change applies to subsequent development builds; do not describe the already published JAR as automatically updated.
- Verification: `./gradlew :neoforge-1.21.1:build :neoforge-1.21.1:verifySharedJarContent --dependency-verification=strict --no-configuration-cache --no-daemon` and `git diff --check`. Expected: successful build, unit tests and archive checks, with no whitespace errors. No new visual capture is claimed by this switch removal.

- Completed verification: Gradle build and archive checks passed; 255 unit tests, 0 failures, 0 errors, 0 skipped. Production source search found no remaining preview-command or startup-property references.
