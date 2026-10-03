# AE2 Federation (NeoForge)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.250-F16436?style=flat-square)
[![License](https://img.shields.io/github/license/ControlNet/ae2-federation?style=flat-square)](LICENSE)

Connect separate **Applied Energistics 2 networks** to share storage, autocrafting, processing machines, and ME power while keeping each network independent.

## Features

- **Shared storage**: access items and fluids across connected ME networks.
- **Remote autocrafting**: craft with another network's pattern providers from your ME terminal.
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

Every block is crafted in survival from early AE2 materials, starting with a **Federation Logic Processor** pressed in the Inscriber from a Logic Processor and Fluix Dust. JEI or EMI shows the recipes, and AE2's in-game guide has an **AE2 Federation** section.

## Getting started

1. Build two separate, powered ME networks.
2. Connect them through an **ME Federation Router**, using a different face for each network. Use **ME Federation Cable** to link Routers over longer distances. For two adjacent networks, an **ME Federation Bridge** can connect them directly.
3. Right-click the Router or Bridge, select the source and target networks, and enable the sharing permissions you need. Permissions apply in one direction; configure the reverse direction separately if needed.
4. Use your normal ME terminal to access the storage and crafting you enabled.

### Remote crafting

With a crafting permission, the target network uses the source network's pattern providers as its own, from its terminals or its automation. The target network's crafting CPU runs the job with the materials it can see, the source network's storage included: a crafting permission always comes with the storage permission of the same direction. The source network needs no CPU, and the result arrives in the target network. A permission set to re-export passes crafting along a chain (A from B, B from C), and two networks may each craft with the other's providers.

### Remote processing

1. Place an **ME Federation Pattern Provider** on the network that sends the work, and an **ME Federation Processing Endpoint** beside the machines.
2. Connect the Federation faces of both to the same Federation network.
3. Right-click the Provider, insert encoded processing patterns, and drag each pattern onto an Endpoint.
4. Results the machines push into the Endpoint return to the Provider's network.

Processing needs no permission. Each Endpoint belongs to one Provider at a time.

## Feedback

[Report a bug or suggest a feature](https://github.com/ControlNet/ae2-federation/issues). For bugs, include your mod versions, what happened, and relevant logs or screenshots.

## Development

See [build and test commands](docs/testing/commands.md), [Gitflow and releases](docs/releasing.md), and [compatibility details](docs/compatibility/matrix.md).

## Acknowledgements

- [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2): the great base mod that this project is based on.
- [LDLib2](https://github.com/Low-Drag-MC/LDLib2): the UI framework used for this project.
- [NeoECO AE Extension](https://github.com/DancingSnow0517/NeoECOAEExtension): its LDLib2-based, AE2-styled GUI implementation is the reference for our GUI.

The Federation Logic Processor texture is edited from AE2's Logic Processor texture and, like AE2's art, is licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), not AGPL-3.0.
