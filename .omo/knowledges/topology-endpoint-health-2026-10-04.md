# Topology Endpoint dot colour (2026-10-04)

`client/policy/EndpointHealth.of(runtimeMode, ownerShown, ready, alone)` decides the dot on an Endpoint node and the
panel's accent; the node carries the undrawn class `health-active|waiting|off` for UI tests.

- **Off (grey) first:** Local runtime mode, or no network on the graph claims it. This wins over everything else, so an
  unclaimed Endpoint without power or subnet stays grey (user choice, 2026-10-04).
- **Waiting (yellow):** claimed, but its subnet node is not active/booted (`nodeReady`) or nothing is connected behind
  it (`subnetAlone`).
- **Active (green):** otherwise.

Why `subnetAlone` was needed: since claimed Endpoints share the owner's ME power (1d8bf09), a claimed Endpoint's node
is almost always ready, so a lone Endpoint (useless: inputs go into the subnet's storage) showed green.

- `EndpointTargetBinding.subnetAlone()` = the subnet Grid holds only the Endpoint's own node (`IGrid.size() <= 1`).
  Energy sharing joins only energy pools, not Grids, so it does not change the count. Any block with a node counts as
  connected, including a lone cable or energy cell.
- The session sends `subnetAlone` beside `nodeReady` in both Endpoint JSONs (topology facts and wires-view targets).
  The panel adds the note `endpoint_node.alone`, the wires view's network fact reads `subnet_alone`, and the
  troubleshooting guide lists the message.
- Tests: `EndpointHealthTest` (rule), GameTest `endpointSubnetEnergy` phase 5 (removing the subnet's chest makes it
  alone), `ui.endpoint` (one claimed node `health-active`, the unclaimed one `health-off`).
