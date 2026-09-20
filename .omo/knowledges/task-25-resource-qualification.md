# Task 25 Resource Qualification

- AE2 19.2.17 item keys preserve data-component identity through generic NBT serialization; native exact-key filters can
  distinguish component variants while the item key-type filter accepts both.
- AE2 fluid storage uses `long` quantities in millibuckets. Task 25 exercises physical item and fluid cells through the same
  `MEStorage` simulation/modulation/listing authority used by Task 21.
- Applied Flux commit `a54eafb72d72bd259bc3b5fa226b4f5542c4c3c4` is the explicit 2.1.3 to 2.1.4 bump. Its
  source metadata and key/cell implementation align with official Modrinth version `sD979rMC` and its embedded metadata.
- Treat that alignment as authoritative ordinary correlation only. There is no reproducible-build or cryptographic
  source-to-binary identity claim.
- The isolated `appfluxTest` source set resolves pinned Applied Flux, GuideME, and Glodium artifacts under strict dependency
  verification. Its source is associated with the test mod only when `enableAppfluxCompatibility=true`; default runs remain
  addon-free.
- The real `FE_CELL_256M` inventory preserves `4,294,967,311` FE through simulation, insertion, listing, simulated
  extraction, modulated extraction, and a final `3,221,225,482` FE quantity without changing AE power.
- Task 25 completed evidence requires five zero-exit children and rejects fake key IDs, wrong source/artifact hashes, missing
  addon execution, canned or truncated quantities, codec/filter bypass, FE/AE coupling, optional leakage, and stale identity.
