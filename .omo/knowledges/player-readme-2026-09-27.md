# Player-facing README

- The repository README is for players. Follow the owner's `minecraft-matrix-bridge` README style: a one-sentence purpose, short feature bullets, exact requirements, installation, and a brief getting-started section.
- Keep architecture contracts, QA gates, benchmark evidence, and development workflows in their existing documentation rather than the homepage.
- Match requirements to `neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml`; the current dependency ranges are exact, not minimum versions.
- Federation is required on both client and server. The current source has no survival recipes, so the README directs users to the Creative tab and identifies the first release as in preparation.
- Do not add download badges for Modrinth or CurseForge until actual project pages exist.
- Validation: `git diff --check` and a standard-library Python check of README relative links and declared dependency versions. No application code changed.
