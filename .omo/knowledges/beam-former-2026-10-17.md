# ME Federation Beam Former: implementation notes (2026-10-17)

Feature: `docs/features/beam-former.md`; plan `.omo/plans/beam-former.md`.

- **Remote links reuse the P2P idea.** `RouterBlockEntity.remotePorts()` lets a subclass add ports beyond its faces; the
  former's anchor names `"beam:" + partner` with a mirrored peer, so the registry merges both ends' domains without new
  evidence fields. `OperatingPowerCosts.beam` reads the same port back to price a link and to count its square for
  converter matching; only the lower node id charges, so a pair pays once.
- **"Wrong size" must compare sizes.** The first `otherSizeAhead` reported any former facing back on the line, so a
  former behind a same-size pair read "another size"; it now skips formers of the same square.
- **Client sync of a Router subclass.** `RouterBlockEntity.loadAdditional` loads AE2 grid nodes, so the former
  overrides `handleUpdateTag` and `onDataPacket` to read only its `BeamLook`. A GuideME scene loads block entities
  through `loadAdditional`, so that also reads the look when the scene's NBT carries one (a saved world never does);
  the scene writes `beam_partner` as `BlockPos.asLong` (x shifted by 38).
- **Renderer.** `CableFlowRenderer.emitCell` is the shared per-cell emit; the beam is drawn one block per step with the
  pose scaled across the square, so pulses keep the cable's pace instead of stretching over the whole length.
- **GameTests and server-wide rules.** Tests run in parallel; a test that changes `BeamLinkRule` or the capacity limit
  would leak into its neighbours, so range is covered by `BeamPartnerIndexTest` only. Power tests set
  `OperatingPowerRule` and turn it off at the end, as the existing power tests do.
- **Guide shots.** Other sessions run guide clients on their own displays; stop only your own process group
  (`setsid` + `kill -- -PID`), never `pkill -f run-guide`.
- **In-game check.** `build/beam-former-check/`: before (linked, nothing drawn), after unpowered (thin red), after with
  the power rule off (cyan), and guide renders in `guide/`.
