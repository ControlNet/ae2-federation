# AE2 Federation (NeoForge)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.250-F16436?style=flat-square)
[![License](https://img.shields.io/github/license/ControlNet/ae2-federation?style=flat-square)](LICENSE)

Connect separate **Applied Energistics 2 networks** to share storage, autocrafting, processing machines, and ME power while keeping each network independent.

## Features

- **Shared storage**: access items and fluids across connected ME networks.
- **Remote autocrafting**: request crafting from another network through your ME terminal.
- **Distributed processing**: map patterns to Processing Endpoints on other networks using the Federation Pattern Provider.
- **ME power sharing**: supply power from one network to another.
- **Directional permissions**: choose what each network can access. Sharing stays off until you enable it.
- **In-game configuration**: view connected networks, edit permissions, and assign processing targets. English and Simplified Chinese included.

## Requirements

- Minecraft **1.21.1** / Java **21**
- NeoForge **21.1.250**
- Applied Energistics 2 **19.2.17** and its dependencies
- LDLib2 **2.2.34** and its dependencies

These are the exact versions required by the current build. Install the mod and its dependencies on **both the client and server** for multiplayer.

## Installation

1. Set up a NeoForge instance with the versions listed above.
2. Download the mod JAR from [GitHub Releases](https://github.com/ControlNet/ae2-federation/releases) and put it alongside its required mods in the instance's `mods/` folder.
3. Launch Minecraft. For multiplayer, install the same mods on the server.

Version **0.0.1** is an early release with no survival crafting recipes; try the blocks from the **AE2 Federation** Creative tab.

## Getting started

1. Build two separate, powered ME networks.
2. Connect them through an **ME Federation Router**, using a different face for each network. Use **ME Federation Cable** to link Routers over longer distances. For two adjacent networks, an **ME Federation Bridge** can connect them directly.
3. Right-click the Router or Bridge, select the source and target networks, and enable the sharing permissions you need. Permissions apply in one direction; configure the reverse direction separately if needed.
4. Use your normal ME terminal to access the storage and crafting you enabled.

For remote processing, add an **ME Federation Pattern Provider** to the source network and an **ME Federation Processing Endpoint** beside the target machines. Connect their Federation faces to the Federation network, insert encoded processing patterns into the Provider, then map them to Endpoints in the Router or Bridge screen and enable processing permissions.

## Feedback

[Report a bug or suggest a feature](https://github.com/ControlNet/ae2-federation/issues). For bugs, include your mod versions, what happened, and relevant logs or screenshots.

## Development

See [build and test commands](docs/testing/commands.md), [Gitflow and releases](docs/releasing.md), and [compatibility details](docs/compatibility/matrix.md).
