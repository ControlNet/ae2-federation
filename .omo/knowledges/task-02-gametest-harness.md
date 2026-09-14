# Task 2 GameTest harness knowledge

- NeoForge 21.1.250 registers 1.21.1 annotated tests through `RegisterGameTestsEvent`; explicit templates require
  `templateNamespace = "ae2federation_test"` and `@PrefixGameTestTemplate(false)`.
- A ModDevGradle run named `gameTestServer` with `type = "gameTestServer"` creates `runGameTestServer`; its source set
  can load a dev-only mod alongside the production source set.
- Minecraft 1.21.1 structure fixtures belong under singular `data/<namespace>/structure/`.
- The test function ID produced for the unprefixed method is the lower-case method name (`harnessnativesmoke`); the
  structure ID remains `ae2federation_test:harness_native_smoke`.
- Empty registration can log `No test functions were given!` while the inner Gradle process exits 0. Evidence wrappers
  must reject this diagnostic and zero executed cases instead of trusting the process exit or success banner.
- AE2 block entities create their Grid node on a later server tick. Resolve and assert the node inside `succeedWhen`,
  not during immediate layout placement.
- A minimal real AE2 smoke fixture is a creative energy cell below an ME chest with a 1k item cell. The live storage
  service supports deterministic modulated insert/extract assertions and measurable simulated inserts.
- Native success evidence is written atomically as a properties artifact to a parent-selected absolute path. Wrapper
  acceptance compares its kind, native test ID, structure ID, assertion count, operation count, and insert/extract
  reconciliation with manifest metadata; log markers are diagnostic only.
- Persisted result schema version 2 is self-validated before a wrapper returns and can be consumed independently with
  `federationVerifyEvidence`. Validation recomputes source/worktree, dependency metadata, and product JAR identities;
  verifies timestamps and requested/executed/assertion accounting; and hashes every relative attempt artifact.
- Worktree identity must exclude only generated build outputs and disposable `run-gametest`/`run-server` directories.
  Including the GameTest world makes a valid attempt appear stale because nested runs create and update world files.
- The benchmark uses 1,000 balanced `Actionable.MODULATE` insert/extract cycles. Evidence requires inserted and
  extracted totals to equal the manifest operation count and elapsed nanoseconds to be positive.
- Schema 3 binds reports to canonical machine-local producer evidence root and attempt path, hashed with run ID.
  Relocation is not reproducibility: rerun at a new root instead of replaying copied evidence. Path integrity hashes
  are not signatures against arbitrary coordinated report rewriting.
- GameTest finalizers preserve logs/crash reports before deleting only run-gametest; nested process cleanup also runs
  in parent finally blocks after timed-out descendants terminate. Direct runs have an explicit cleanup task/receipt.
- Final independent verification confirmed canonical-only schema-v3 consumption rejects copied roots, renamed attempts,
  symlink aliases, malformed root metadata, and edited path identity; real success/failure/timeout/no-test/benchmark runs
  all preserved diagnostics and left no run-gametest tree or session lock.
