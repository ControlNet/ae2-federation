# Player-facing README

- The repository README is for players. Follow the owner's `minecraft-matrix-bridge` README style: a one-sentence purpose, short feature bullets, exact requirements, installation, and a brief getting-started section.
- Keep architecture contracts, QA gates, benchmark evidence, and development workflows in their existing documentation rather than the homepage.
- Match requirements to `neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml`; the current dependency ranges are exact, not minimum versions.
- Federation is required on both client and server. The current source has no survival recipes, so the README directs users to the Creative tab and identifies the first release as in preparation.
- Do not add download badges for Modrinth or CurseForge until actual project pages exist.
- Validation: `git diff --check` and a standard-library Python check of README relative links and declared dependency versions. No application code changed.
- 2026-10-02: the README had grown from 406 to 687 words because feature commits appended mechanism rules to
  "Getting started" (Federation Domain membership, return buffers, release rules, native Provider behavior) and the
  0.0.3 preparation added a long crafting paragraph. The owner called it a mess. It now has the four connection steps,
  then short "Remote crafting" and "Remote processing" subsections with only what a player does and must know.
  A feature commit updates the README only when a player-visible step changes, in one short sentence; rules and
  edge cases go to `docs/architecture/` or the release notes.
- 2026-10-03: grown back to 694 words, with stale steps (selecting source and target networks, "permissions" where
  the screen says rules). Rewritten to 414: feature bullets, requirements, installation, four connection steps naming
  the Storage, Crafting and ME power switches, and one paragraph for remote processing. Remote crafting needs no
  section of its own (switch on Crafting, order from the terminal). Mechanism details (re-export chains, CPU side,
  one Provider per Endpoint, subnets, return buffers) live in the AE2 in-game guide pages under `ae2guide/`.
- Same day, the owner cut further: no survival/guide/JEI paragraph and no Getting started section at all. The README is
  now purpose, features, requirements, installation, feedback, development links and acknowledgements (264 words).
  How to play lives only in the in-game guide; do not add setup steps back. Then also cut: the "while keeping each
  network independent" clause, the client/server and tested-versions note under requirements. Features are plain
  one-line sentences, no bold "Label: explanation" pattern (the owner found it too AI-sounding).
