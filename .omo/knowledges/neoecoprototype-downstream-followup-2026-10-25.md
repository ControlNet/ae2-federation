# Prototype downstream follow-up (2026-10-25)

Read-only GitHub API inspection found upstream main at `286ef5761de74afb8df08f3b087e496ac919b4c5`.
GitHub releases and Modrinth project `3HO3APHb` still list 1.3.9 as latest. Main's build.gradle says
1.3.10, but that is not evidence of a published artifact.

- N1's geometry hook remains PR #11, merged as `e02f8bc`: protected `validateInterfaceFace` lets a
  subclass replace the interface-side face while inheriting the rest of L1 validation.
  https://github.com/reliqwq/NeoECOPrototype/pull/11
- Additional downstream API fix `933fd6e` (October 22, before PR #11) changes
  StorageMatrixRegistration.registerFinite to compare against DeferredRegister.getNamespace(), rather
  than the item registry's minecraft namespace. Legal addon registrations previously failed.
  The GameTest checks rejection and queuing, not complete item registration after registry freeze.
  https://github.com/reliqwq/NeoECOPrototype/commit/933fd6e964f414bb41bf296169ee43f5d0ff2892
- `b25c59b` adds CI enforcement of api-layer dependency boundaries, with a scoped exception for the
  registration factory's concrete item return type. This is maintenance protection, not a new N1 hook.
- `23896d9` publishes generic inventory and ME storage capabilities for powered/superconductive
  interfaces; F1 machine interfaces are explicitly excluded. `41aa48f` publishes the F1 smart pattern
  bus's existing filtered item handler, allowing pipes to move encoded patterns.
- The public main comparison after #11 did not reveal another N1-specific extension hook.
  Do not infer unpublished/private improvements from the author's reported statement.

No game code changed and no runtime tests were run for this inspection.
