# Retained Endpoint cleanup

Removing the last mapping unpublishes new work. A Lane that has dispatched any work retains its Claim and return
binding: empty native buffers do not reveal what is still inside the machine. Dispatch history is neither completion
proof nor proof of an unfinished task. There is no extra crafting ledger.

The Domain mapping selector includes retained identities from the selected Provider even when they are absent from
the online Endpoint list. Selection follows identity across list changes. Two presses confirm the same Provider
instance, Lane index, binding revision, mapping change version, Endpoint identity/epoch, remote entity instance and
observed Claim state. A changed observation requires a fresh confirmation. Normal menu/server authorization still applies.

Explicit release has two outcomes:

- `released-<lane>`: the loaded Endpoint still has the expected identity, owner and Claim epoch, and `releaseClaim`
  succeeds. A false compare-and-set returns `rejected-claim-changed`; the binding is retained.
- `cleared-stale-<lane>`: the position is loaded and the old Endpoint is absent, replaced, or no longer holds the
  expected Claim. Only the Provider's local binding is retired. The replacement Endpoint and other owners are untouched.

`rejected-endpoint-unloaded` is not absence evidence. Neither explicit cleanup nor idle maintenance force-loads chunks.
Idle maintenance retires never-dispatched bindings only after a successful release of the matching loaded Endpoint.
Missing/replaced bindings remain available for explicit cleanup.

## Native resources and recovery

- A nonempty native `sendList` rejects release/cleanup as `rejected-pending-send`. Its authorization stays bound to the
  old Endpoint and cannot deliver to a replacement. Restore the original Endpoint/Claim and access so AE2 can finish
  sending. If that is impossible, cancel the CPU request and dismantle the Provider to collect native buffer drops;
  manually recover anything already inside the machine. Rebuilding creates fresh bindings. Dismantling is destructive
  to configuration and must be an explicit player action.
- A nonempty native `returnInv` rejects cleanup as `rejected-pending-return`. Restore source power/storage capacity so
  AE2 can inject it; alternatively collect native drops by explicitly dismantling the Provider. Cleanup never empties it.
- Once both buffers are empty and release/cleanup succeeds, reset the native crafting lock using AE2's existing method.
  This is an explicit abandonment of the return association, not an assertion that the CPU request completed. Products
  still in the machine need manual recovery and any waiting native request must be cancelled or otherwise fulfilled.
- Retirement increments the Lane revision and updates `ProviderRuntime.bindLane`; allocation increments it again.
  Old target authorizations are stale, and Lane reuse additionally requires empty native send and return buffers.

Return rebinding continues to inspect only the Provider's own Lanes at the existing maintenance interval. Neither
cleanup nor identity initialization adds a per-tick world scan.
