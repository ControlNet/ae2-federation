# A domain of one ME network in the Domain screen (2026-10-19)

- `FederationDomainPolicySession.selection` is the rule pair editor's state (`PolicyEditorSelection`), which needs
  two distinct members. It used to double as "the domain's networks": with one network it is null, and
  `workspaceChoices()` returned before writing networks, Providers and Endpoints, so the topology stayed empty and
  the header read "No confirmed Federation Domain membership" although the domain was confirmed.
- `members()` is now the domain's networks (the pair's order when there is a pair, else sorted by ID). Use it for
  anything that shows networks; keep `selection` for the rule pair only (consumer/provider/capability choices,
  revisions, `authorizeAction`).
- A confirmed domain with at least one network starts `READY` and editable, so its network can be renamed and
  statistics pinned; rule actions still need `selection` and refuse without it.
- GameTest `adomainofonenetworkshowsitsnetwork`: Switch + one ME Chest + one cable, `forRouter(...)`,
  `workspaceChoices()` lists one network and `statusCode()` is `ready`.
- Compare: `build/topology-one-network-check/compare.png`.
