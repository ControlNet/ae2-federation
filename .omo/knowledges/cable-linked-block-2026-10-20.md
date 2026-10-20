# One rule for cable links and cable arms: CableLinkedBlock (2026-10-20)

User report: "the Beam Former is not drawn connected to Federation cable", plus "check that everything Federation
cable can link to renders the connection".

## Cause

- The server and the client each had their own list of which device faces link.
  - Server: `RouterBlockEntity.links(face)`, overridden by the Beam Former, Beam Converter and Quantum Cube entities.
  - Client: `CableVisualConnections.kind`, which hand-listed Router, Converter, Quantum Cube, Provider and Endpoint.
- The Beam Former was never added to the client list, so a cable at its back linked but drew no arm.
- `BundleContacts.contact` had a third rule (`FormableDevice.covers`, true except on the Quantum Cube). A bundle
  against a Former's or Converter's side therefore counted as a whole contact although that face does not link.

## Fix

- `router/CableLinkedBlock.linksCable(state, face)` reads the block state only, so it works on both sides.
- Implemented by:
  - `RouterBlock` (Router and Switch): `formed`;
  - `BeamFormerBlock`: formed and (back or toward the body), with `BeamFormerBlock.faces`;
  - `BeamConverterBlock`: formed and (front or toward the body), with `BeamConverterBlock.faces`;
  - `QuantumCubeBlock`: `links`;
  - `FederationPatternProviderBlock` and `EndpointBlock`: the AE2 facing (front).
- Its users:
  - `RouterBlockEntity.links` is now final and asks the block.
  - `CableVisualConnections.kind` draws DENSE where the neighbour's face links.
  - `BundleContacts.contact` counts only faces that link; `FormableDevice.covers` is gone.
- Federation P2P tunnels are parts, not blocks. They keep their own COVERED path in `kind`.
- Behaviour change: an unformed Router (or any device) gets no arm, because it links nothing.
  - `cablenecksonlyatdevices` now waits for the Router to form.
  - A placed Router forms on the next update, so in game the arm appears at once.

## Audit and test

- Federation ports come from:
  - `FederationPortCapability` registrations: Router/Switch, Former, Converter (+ creative), Quantum Cube, Provider,
    Endpoint and the P2P part;
  - `federationDomainPort` overrides.
- Every one of those blocks implements `CableLinkedBlock`.
- A new block that takes Federation cable must implement it, or `RouterBlockEntity.links` returns false.
- On the function branch this includes the Automation Interface and the Computing Device.
- GameTest `everylinkedfacedrawsanarm` (`CableLinkParityGameTests`) puts a cable on each probed face of every
  device. It checks "the cable's domain holds the device" == "the cable draws an arm toward it", plus the expected
  count of linked faces per device.
- Images: `build/cable-link-check/`.
