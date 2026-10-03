# GUI declutter and AE2 toolbar tabs, 2026-10-03

## Rail tabs compared with AE2 and NeoECO

- AE2 19.2.17 `IconButton.renderWidget`: an 18x20 `states.png` sprite (176,128 normal, 212,128 hover, 194,128 keyboard
  focus) drawn at its own size, the 16x16 icon one pixel inside its top-left corner. Hovered, it swaps to the hover
  sprite and draws sprite and icon one pixel lower (`yOffset = isHovered() ? 1 : 0`). The hover sprite's lip is one
  pixel and its last row is transparent, so the button reads as pressed in.
- NeoECO c33f736 `UploadButton` copies that exactly. Its LDLib2 `toolbarButton` (crafting host) uses `RECT_RD*`
  sprites, a different control.
- Federation's rail tabs were 22x24, stretching the sprite, with the icon centred over the lip and no drop on hover.
  Now: `.rail-tab` 18x20, rail 22 wide; `FederationTheme.toolbar/toolbarHover/toolbarSelected` draw sprite plus icon
  with the one-pixel drop, set per tab in `FederationWorkspace.show`. The open page uses the focus sprite.
- Checked in `ui-rail-tab-hover` (graph-controls) at 6x: crisp pixels, hovered tab one pixel lower with a one-pixel lip.

## What the player no longer sees

- Revisions anywhere: rule states ("Off", "Active", ...), the footer's rule/topology revision label (removed with
  `revisionsText`), accepted/stale/conflict statuses. Revisions still travel hidden for stale-edit authority.
- Server chatter: footer ready text is empty; "Server confirmed:" / "Rejected by server:" prefixes are gone.
- Tooltips on topology cards and Endpoint nodes. An Endpoint's recent deliveries/returns moved into its panel text.
  Cards carry an undrawn `network-<uuid>` class so tests still find them after a rename.
- Rule switch tooltip (mode list, mouse hints, crafting-holds-storage and re-export notes) and the `topology.mode.*`
  keys. The state label's tooltip is only `attention(...)`: the reasons of an error/waiting rule without the "Last
  backend check:" headings, or the skipped-storage line of a working storage rule; none otherwise.

## Test notes

- graph-controls' storage rule is Active or waiting for storage depending on which fixture network ends up the
  provider; the tooltip check accepts both (Active: none; waiting: only "The other network has no storage it can
  share."), and waits because the runtime arrives after the switch.
- The mapping fixture has no rule pill; to return to the pair editor select a card, then its `.network-link`.
- `federationUiVerifierSelfTest` already failed before this change for `forged-task15-ack` (its regex `policy-N$`
  never matched the visible status) and `claim-conflict-success` (Task 33); Task 34's probes all reject.

## Still shown, candidates to trim (not changed)

Endpoint "Identity and ownership" panel (UUIDs, instance/claim/runtime epochs, result code, native network UUID);
claim epochs on the processing page and in the release dialog; Endpoint facts "Return binding" and "Last claim
result"; "后端未就绪" in the legend; "运行：" prefixes; the always-on pair note and energy note; the transient
"waiting for server confirmation" texts. Note `ui.reject-claim-conflict`'s verifier reads `OWNER_CONFLICT` and
`Claim epoch: 1` from the identity panel, so trimming it needs that evidence moved.
