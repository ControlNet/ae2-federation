# Policy switches and in-flight requests (2026-10-04)

`FederationDomainPolicyMenuHolder.renderRequestProgress` runs on every send and reply. It used to pass
`active && authorized` to the topology, with `active` false while a request was in flight, so every rule and Endpoint
energy switch was rebuilt locked and then unlocked when the reply landed. Once a locked switch got its own
half-strength sprite (`SWITCH_*_LOCKED`, 1d8bf09), that showed as all switches flashing on each press.

- The topology now gets `active(serverStatus) && authorized`, ignoring the in-flight request. Nothing is lost:
  `FederationMenuAuthority.send` already drops a second action while one is pending, and the sync lamp still turns
  yellow.
- The wires (mapping) page and the Provider screen still lock during a request on purpose: `ui.mapping`'s "rapid
  mapping clicks submit once" pins the Map button going inactive as the save feedback.
- Regression check: `ui.shared-policy` presses the Router's storage switch through `context.input()` and, in the same
  step, requires `#ack_status` to be `pending` and `#policy_switch_0_crafting` still active. A plain `.click` step is
  too slow: the reply lands before the next step.
