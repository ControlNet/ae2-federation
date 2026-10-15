# Endpoint network labels (2026-10-13)

Found in the real-game check of the hero scene: an idle Endpoint read "Waiting for its ME node" in the topology, and
the Provider screen titled every Endpoint "Endpoint · outside the domain".

- **Why the node waits.** A Federated Endpoint's subnet usually has no energy of its own; it is powered only through
  the shared pool once a Provider maps (claims) it. Mapping does not need a ready node (`mappingTargetState`), and
  `endpointsubnetenergy` covers the claim powering it. So an unpowered, unclaimed Endpoint now says
  `endpoint_energy.unclaimed` ("Shares power once a Provider maps this Endpoint."); any other unready node keeps
  "Waiting for its ME node". The topology facts carry `nodePowered` for this.
- **Why "outside the domain".** The Provider screen lists only Endpoints of its own domain
  (`FederationDomainGraphProjection.endpointEntries`), so every listed Endpoint is in the domain. The label came from
  looking the subnet up among the member networks (networks a Switch attaches); a subnet is seldom one. The owner
  decided: title "Endpoint @ x, y, z"; keep the "Network" row, named the way the topology's "Native ME network" fact
  names it: member name, else "Network C556" from the confirmed identity, else "Identity not confirmed"
  (`EndpointText`). The card's second line now shows that network, since the title holds the coordinates. The
  Provider-side "outside the domain" fallback stays for Provider cards, whose network is always a member.

Before/after: `build/jei-jade-check/endpoint-text-before-after.png`.
