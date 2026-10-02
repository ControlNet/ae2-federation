# AE2 Federation (NeoForge)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.250-F16436?style=flat-square)
[![License](https://img.shields.io/github/license/ControlNet/ae2-federation?style=flat-square)](LICENSE)

Connect separate **Applied Energistics 2 networks** to share storage, autocrafting, processing machines, and ME power while keeping each network independent.

## Features

- **Shared storage**: access items and fluids across connected ME networks.
- **Remote autocrafting**: request crafting from another network through your ME terminal.
- **Distributed processing**: map patterns to Processing Endpoints on other networks using the Federation Pattern Provider.
- **ME power sharing**: two networks share one energy pool, the way a Quartz Fiber joins them, with a single switch per pair.
- **Directional permissions**: choose what each network can access from the other (energy is shared both ways). Sharing stays off until you enable it.
- **In-game configuration**: view connected networks, edit permissions, and assign processing targets. English and Simplified Chinese included.

## Requirements

- Minecraft **1.21.1** / Java **21**
- NeoForge **21.1.250**
- Applied Energistics 2 **19.2.9 or newer** for Minecraft 1.21.1 and its dependencies
- LDLib2 **2.2.34 or newer** for Minecraft 1.21.1 and its dependencies

Regular builds and tests use AE2 19.2.17 and LDLib2 2.2.34; see the [minimum-version investigation](docs/compatibility/minimum-versions.md) for the lower bounds. Newer versions are allowed by the loader, but have not all been compatibility-tested. Install the mod and its dependencies on **both the client and server** for multiplayer.

## Installation

1. Set up a NeoForge instance with the versions listed above.
2. Download the mod JAR from [GitHub Releases](https://github.com/ControlNet/ae2-federation/releases) and put it alongside its required mods in the instance's `mods/` folder.
3. Launch Minecraft. For multiplayer, install the same mods on the server.

Version **0.0.3** is an early release with no survival crafting recipes; try the blocks from the **AE2 Federation** Creative tab.

## Getting started

1. Build two separate, powered ME networks.
2. Connect them through an **ME Federation Router**, using a different face for each network. Use **ME Federation Cable** to link Routers over longer distances. For two adjacent networks, an **ME Federation Bridge** can connect them directly.
3. Right-click the Router or Bridge, select the source and target networks, and enable the sharing permissions you need. Permissions apply in one direction; configure the reverse direction separately if needed.
4. Use your normal ME terminal to access the storage and crafting you enabled.

With a crafting permission, the target network's terminals, crafting monitors and automation list what the source network can craft and request it there; the source network crafts it with its own patterns, crafting CPUs and materials, and the result arrives in the target network. The source network needs its own patterns and crafting CPU. Requests pass along chains of crafting permissions (A from B, B from C) without a direct permission between A and C; crafting permissions cannot form a loop.

For remote processing, add an **ME Federation Pattern Provider** to the source network and an **ME Federation Processing Endpoint** beside the target machines. Connect their Federation faces to the Federation network, insert encoded processing patterns into the Provider, then map them to Endpoints. No rule is needed: every Provider whose Federation face joins the Endpoint's Federation Domain can map it, and one Provider owns an Endpoint at a time. The Provider's network being in that domain by another route (a Router face, a Bridge) is not enough. A native AE2 Pattern Provider on the Endpoint's Federation face instead uses it locally. Right-clicking the Provider opens its own screen: put encoded patterns into its nine slots, drag a pattern's port onto an Endpoint to map it, and set Blocking mode, Lock Crafting, Pattern Access Terminal visibility and priority there. Endpoints that other Providers own are shown read-only. Machine results pushed into any of the Endpoint's five subnet faces (by the machine itself, a pipe or an Export Bus) return through that Endpoint's own buffer to the Provider's network; an Endpoint cannot be released while its buffer holds results. The Router and Bridge screens offer the same mapping for every Provider of the domain.

## Feedback

[Report a bug or suggest a feature](https://github.com/ControlNet/ae2-federation/issues). For bugs, include your mod versions, what happened, and relevant logs or screenshots.

## Development

See [build and test commands](docs/testing/commands.md), [Gitflow and releases](docs/releasing.md), and [compatibility details](docs/compatibility/matrix.md).
