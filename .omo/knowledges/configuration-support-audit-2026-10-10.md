# Configuration support audit

Date: 2026-10-10. Scope: current production sources in `common/src/main` and `neoforge-1.21.1/src/main`.

No `ModConfigSpec`, `registerConfig`, or `ModConfig.Type` usage was found. Inspection of
`neoforge-1.21.1/src/main/java/space/controlnet/ae2federation/neoforge/NeoForgeEntrypoint.java`
confirmed registration of content, payloads, and lifecycle listeners, but no mod configuration registration.
Searches for configuration readers and filesystem access found no custom gameplay configuration loader.
`ae2federation.artifactProof` is a JVM boolean used by the server/client entrypoints for artifact validation,
not a pack-author gameplay setting. Resource mixin configuration is not a gameplay configuration file.

Policy classes in `common/src/main/java/space/controlnet/ae2federation/policy/` concern network-sharing
rules and persistence; they are not global pack configuration. Do not infer the existence of server TOML
settings from Policy or from the optional-power idea.

The user requested configurable expert-style restrictions. The
[idea](../../docs/ideas/configurable-gameplay-rules.md) separates this direction from assistant proposals
for independent switches, presets, server authority, and safe world transitions. No configuration API,
filename, defaults, key names, or runtime changes were implemented or validated by a game launch.

Follow-up user decision: default configuration should expose the full intended gameplay. Power requirements
and cable capacity enforcement are independently configurable and both enabled by default. Base cable
capacity applies only while capacity enforcement is on; energy costs apply only while power is required.
Numerical defaults and schema remain open. This supersedes the assistant's disabled-by-default/preset
proposal. Pack-author opt-outs are deliberate customization, not an access restriction. No implementation
was changed, and the default-on direction does not activate unimplemented ideas in the current mod.
