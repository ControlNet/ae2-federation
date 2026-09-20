# Task 29 Native Crafting Lifecycle

## Authority

AE2 owns calculation, CPU work, cancellation, completion, callbacks, and physical insertion. Federation retains only the
native requester/link pair keyed by policy, requester node lineage, and `MultiCraftingTracker` slot. Terminal state is read
from `ICraftingLink`; no Federation job-state machine exists.

Link UUID ownership prevents a fresh node at the same coordinate from claiming old work. Capability withdrawal does not
remove this mapping, cancel native work, or authorize resubmission.

## Cycle Boundary

Enabled Crafting request policies form consumer-to-provider dependency edges. An edge is cyclic when its provider reaches
its consumer. The guard runs before backend discovery and withdraws every cyclic relationship.

## Qualification

The exact cases are `crafting.cancel-native`, `crafting.disconnect-restart`, `crafting.reject-cycle`,
`crafting.reject-replay`, and `crafting.replace-requester`. The disconnect case reloads requester NBT with the same link
UUID, restores the Bridge, rejects duplicate submission, closes an actual terminal-session reference, and observes 128
sticks through requester callback and physical ME cell insertion.

The request registry is bounded by two correlated maps: one `NativeCraftingRequestKey` entry and one exact crafting-UUID
owner entry per live request. `NativeTerminalRequest.submitTracked` registers the owner/link immediately after the real
`MultiCraftingTracker` submission, and `synchronizeTracked` performs safe reload rebinding. A loaded requester may replace
the prior object only when the same node lineage and slot key present the same UUID from its live tracker. Production
registry polling removes completed or canceled links from both indexes atomically before whole-level close.

The disconnect case begins and submits through one exact `NativeTerminalSession` request, closes that session, proves a
public active API rejects post-close use, then observes the same UUID complete into the exact preauthorized cell.

Lifecycle authority is established before submission and extended only by native hooks for tracker calls, CPU result
delivery, requester callbacks, exact `BasicCellInventory` insertion, and terminal notification. The semantic artifact cannot
authorize these receipts; the coordinated-forgery probe rewrites every semantic identity while leaving the independent
authority unchanged and must be rejected. Each child has an exact semantic property/trace set; unknown facts are rejected
before value checks, including a fully hash/path/time-rebound `nativeFailure=true` probe.

Canonical repaired evidence:
`.omo/evidence/task-29-final-repair/attempt-20260920T161953674Z/result.json`.
