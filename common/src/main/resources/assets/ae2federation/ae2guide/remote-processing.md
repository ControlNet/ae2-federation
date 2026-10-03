---
navigation:
  parent: index.md
  title: Remote Processing
  icon: ae2federation:processing_endpoint
  position: 30
---

# Remote Processing

A <ItemLink id="ae2federation:pattern_provider" /> on one network can send processing patterns to machines that
belong to another network, through a <ItemLink id="ae2federation:processing_endpoint" /> beside those machines.
The results come back to the Provider's network. Processing needs no rule in the Federation screen.

<Row>
  <RecipeFor id="ae2federation:pattern_provider" />
  <RecipeFor id="ae2federation:processing_endpoint" />
</Row>

## Setting up

1. **The machines' network.** Build a small ME network (a processing subnet) whose storage feeds the machines, for
   example with storage buses or interfaces facing them, the usual AE2 way. Place the Endpoint so that one of its
   ME faces joins this subnet. The subnet must not be the Provider's own network.
2. **Face both fronts to the Federation side.** Both blocks have one Federation face, their front, which faces the
   block you clicked when placing them. Click Federation Cable (or a Router) to place them, or put the two fronts
   against each other. The Provider's other five faces join its own ME network like a normal pattern provider, and
   it uses one channel there.
3. **Insert patterns.** Right-click the Provider and put encoded processing patterns in its nine slots.
4. **Map each pattern.** Drag a pattern onto an Endpoint in the wiring graph, or click the pattern and then the
   Endpoint. One pattern can go to several Endpoints and one Endpoint can take several patterns. Only mapped
   patterns can be requested.

When the Provider's network crafts, the inputs go into the subnet's ME storage, and from there to the machines.
Machines (or pipes) must push their results into one of the Endpoint's faces other than its front. They go to the
Provider's return buffer for that Endpoint, then into the Provider's network. Results left in the subnet's storage
do not return.

## One Provider per Endpoint

An Endpoint belongs to one Provider at a time. Mapping a pattern claims it; other Providers show it dashed and
read-only, and dropping a pattern on it is refused with the name of its owner. To hand an Endpoint over, remove its
mappings and release it from the Provider that owns it. Release waits until nothing is still being sent and its
return buffer is empty.

Two Endpoints mapped by the same Provider must sit on different subnets.

## Local mode

An Endpoint can also serve an ordinary AE2 <ItemLink id="ae2:pattern_provider" /> block on another network: place
that pattern provider against the Endpoint's front. The Endpoint then works in local mode and cannot take
Federation patterns until the native provider is removed.
