# Cable capacity counting

Federation Cables count the capacity they would carry under the rules confirmed in the
[cable capacity idea](../ideas/multiblock-routing-and-cable-capacity.md#confirmed-counting-rules). Nothing limits a
cable yet: the count is shown read-only, and limits come later.

## Inputs

The count reads the domain registry's evidence only. Each node's evidence carries its kind
(`FederationDomainNodeKind`): Cable, Router, Switch, Pattern Provider, Endpoint or P2P tunnel, set by the block or
part that publishes it. Links count only when both sides name each other, exactly as for the domain itself.

- **Units.** A Switch node's settled `Native` ports give one unit per distinct `NetworkId`; unsettled faces give none.
  An Endpoint node is one unit, and so is a Pattern Provider node: a device of its own, not deduplicated against
  the network behind it (whose `Native` port is not a unit).
- **Router bodies.** Router nodes linked to each other form one body. Two Router bodies cannot touch (the cuboid check
  rejects it), so a linked component of Router nodes is exactly one formed device.
- **Regions.** Cable, Switch and P2P tunnel nodes linked to each other form a region: a Switch passes a region through
  and a P2P tunnel pair joins the regions at its ends. Router, Provider and Endpoint nodes are outside every region.

A region's routers are the bodies linked to any of its nodes; its units are its Switches' networks plus the Endpoints
and Providers linked to it. A region with at most one router, together with those units, is a **subdomain**: every cable in it counts
the subdomain's units (`SUBDOMAIN`). With two or more routers every cable counts the Federation domain's (`DOMAIN`). An Endpoint, Provider or Switch linked straight to a Router belongs to the domain's count but to no region.

## Server and display

`CableCapacityCounter.count` is a pure function of one domain's evidence (`CableCapacityCounterTest`).
`CableCapacity.demand(level, pos)` counts a domain the first time one of its cables is asked about and keeps the result
until the registry's `evidenceRevision` changes; any node or Bridge evidence change advances it, including link changes
that leave every domain's node and member sets alone. The cache is server-thread state and is dropped when the server
stops.

Jade is an optional compile-time dependency, in its own `jadeApi` configuration so it stays off every run and test
classpath. `compat/jade/FederationJadePlugin` is found by Jade's `@WailaPlugin` scan
and is the only class that refers to Jade, so the mod runs without it. Its server data provider writes the demand of the
cable being looked at; the client line reads `Capacity count: N (subdomain)` or `(Federation domain)` (zh: 子域 / 联邦域). Both sides need Jade for
the line to appear. The GameTest `capacity.cable-regions` checks the counts in a real level. The guide page `network-capacity.md`
explains the rules to players.
