# Network tags are six digits (2026-10-20)

User decision: every player-facing short name of an ME network uses the first six hexadecimal digits of its
NetworkId (UUID), such as "Network 082A5C".

- One helper: `NetworkId.tag(String)` with `NetworkId.TAG_DIGITS = 6`.
- Used by:
  - the topology card and detail title (`FederationTopologyView.displayName`);
  - the merge "Contains" parts, which used eight digits before;
  - the Endpoint subnet text (`EndpointText`);
  - the statistics network chips.
- The function system's script ID for a network (`network-082a5c`, feature branch) is the same digits, so a player
  reads the ID off the topology card. Before, cards showed four digits and scripts used a random ID.
- Endpoint and domain tags are not network tags and keep their own lengths.
- UI tests derive the expected name through the same helper (`TaskThirtyThreeScenarioSupport.networkTag`).
