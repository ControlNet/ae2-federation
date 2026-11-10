# Domain screen sync budget (2026-11-07)

Finding #2 of `project-review-2026-11-07.md`. LDLib2 2.2.34 `DataBindingBuilder.stringS2C` encodes with
`ByteBufCodecs.STRING_UTF8` (32767 chars); a longer value throws in `UISyncManager.tick()` on the server thread. All
changed bindings of one tick go out in one `PacketModularUISync` (vanilla clientbound payload ceiling 1 MiB).

## Measured (`DomainMenuGameTests`, `AE2F_SYNC` log lines)

- Workspace choices: 631 chars per Endpoint in `endpoint` + 444 in `target` (Endpoints without subnet energy; real
  ones carry a few more fields). One network with 64 Endpoints: 70,951 chars. Limit reached at about 30 Endpoints.
- Provider screen choices: 444 per Endpoint (`target`); 64 Endpoints = 29,737.
- Per member network with a storage and a power rule: networks 170 + consumer/provider choices 146 + rules 2 x 202
  = about 720. 65 networks = 47,418 chars; limit at about 45 networks.
- Overview: about 500-680 per network of ~20 blocks (65 networks = 44,337); 256 blocks per network would add about
  4,000 each (estimate).
- Graph: about 114 per member, 135 per Endpoint; 64 Endpoints = 8,731. The projection throws above 256 members
  (`ObservationLimits`, finding #1) or 512 graph entries.

## What was done

- `client/policy/SyncBudget`: a text that fits is sent unchanged; a longer one keeps the head of its long lists plus
  the selected entries, cut to one common size ("water level"), and lists the dropped counts under `omitted`.
- Choices: networks (with consumer/provider choices, rules and revisions of the shown networks only), related networks
  (with their rules), `endpoint`, `target`, `mapping_provider`. Provider screen: `target`, `lanes`. Flows: head.
  Overview: blocks/devices per network capped 64 -> 16 -> 4 -> 0, then head. Graph: Endpoint nodes and patterns
  dropped first (the client reads only member states and member->Provider edges), then a head of members/Providers.
- Graph stopgap: an `IllegalArgumentException` from the projection's limits returns "" (client keeps its last graph)
  and logs once; the limits themselves are finding #1.
- Not done: widening the transport (a `ByteArrayTag` via `tagS2C` would lift the 32767 cap but 1000 networks are
  ~720K chars of choices, beyond the 1 MiB packet); paging would be the real fix at the 1000-subnet scale.
