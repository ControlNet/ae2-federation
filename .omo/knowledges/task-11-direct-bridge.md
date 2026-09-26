# Task 11 Direct Multipart Bridge

## Contract

The production Bridge is a direct-only AE2 multipart part. Its main node remains on the cable bus, while a separately
managed outer node is exposed on the part face. The Bridge classifies two native attachment domains but never joins them.

## Native qualification

Qualification requires the main node to have a native Grid and ordinary cable-bus connections, plus an outer node that
resolves through `NativeAttachmentResolver.resolve(level, position, face, outerNode)`. The resolver's face-edge proof is
the boundary condition for the externally attached native domain. The main node must not be required to have an
in-world connection itself: that edge belongs to the separately exposed outer node.

## Fail-closed statuses

- `VALID`: two distinct native Grid objects and a typed membership candidate.
- `MISSING_MAIN_ATTACHMENT`: the cable-side main node is absent, ungridded, or not connected.
- `MISSING_OUTER_ATTACHMENT`: no adjacent native face-edge attachment exists.
- `SAME_GRID`: both sides resolve to one native Grid.
- `FEDERATION_CABLE_UNSUPPORTED`: the adjacent candidate is non-air but is not a supported native attachment.
- `REMOVED`: the part has been removed and its owned outer node has been destroyed.

## Runtime evidence

The five canonical cases are `bridge.valid`, `bridge.invalid`, `bridge.same-grid`,
`bridge.reject-federation-cable`, and `bridge.reload-replace`. They are topology checks, so each emits one operation
with zero transfer work. The persisted consumer parses the five native properties files and their runtime trace logs,
checks exact artifact cardinality, and rejects fully rebound identity, case-reason, or incomplete-trace mutations.

## Verification

```bash
./gradlew --no-daemon --dependency-verification=strict federationVerify \
  -Pcases=bridge.valid,bridge.invalid,bridge.same-grid,bridge.reject-federation-cable,bridge.reload-replace \
  -PevidenceDir=.omo/evidence/task-11
```

Then pass the generated `result.json` to `federationVerifyEvidence` and
`federationTaskElevenEvidenceSelfTest` as documented in `docs/testing/commands.md`.
