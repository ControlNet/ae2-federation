# AE2 Federation (NeoForge)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.250-F16436?style=flat-square)
[![License](https://img.shields.io/github/license/ControlNet/ae2-federation?style=flat-square)](LICENSE)

Connect separate **Applied Energistics 2 networks** to share storage, autocrafting, processing machines, and ME power while keeping each network independent.

## Features

- **Shared storage**: use another network's items and fluids from your own terminals.
- **Remote autocrafting**: order from another network's pattern providers.
- **Remote processing**: send patterns to machines on other networks with the Federation Pattern Provider.
- **Shared ME power**: join two networks' energy with one switch.
- **Your choice, per direction**: each network decides what the other may use. Nothing is shared until you switch it on.

English and Simplified Chinese are included.

## Requirements

- Minecraft **1.21.1** / Java **21**
- NeoForge **21.1.250**
- Applied Energistics 2 **19.2.9 or newer**
- LDLib2 **2.2.34 or newer**

Install them on **both the client and the server**. Tested with AE2 19.2.17 and LDLib2 2.2.34 ([version notes](docs/compatibility/minimum-versions.md)).

## Installation

1. Set up a NeoForge instance with the versions above.
2. Put the JAR from [GitHub Releases](https://github.com/ControlNet/ae2-federation/releases) and its dependencies in the `mods/` folder.
3. Launch Minecraft.

Every block is craftable in survival. AE2's in-game guide has an **AE2 Federation** section with recipes and setup. JEI users need [AE2 JEI Integration](https://www.curseforge.com/minecraft/mc-mods/ae2-jei-integration) to see AE2 machine recipes.

## Getting started

1. Build two separate, powered ME networks.
2. Connect them: an **ME Federation Bridge** for networks side by side, or an **ME Federation Router** (one network per face) and **ME Federation Cable** for networks further apart.
3. Right-click the Router or Bridge, click the line between two networks, and switch on **Storage**, **Crafting** or **ME power**. Each rule works in one direction.
4. Use your normal ME terminal.

**Remote processing**: place an **ME Federation Pattern Provider** on your network and an **ME Federation Processing Endpoint** next to the machines, with both fronts on Federation Cable. Right-click the Provider, insert processing patterns, and drag each pattern onto an Endpoint. Results pushed into the Endpoint come back to your network.

## Feedback

[Report a bug or suggest a feature](https://github.com/ControlNet/ae2-federation/issues). For bugs, include your mod versions, what happened, and logs or screenshots.

## Development

[Build and test](docs/testing/commands.md) · [Gitflow and releases](docs/releasing.md) · [Compatibility](docs/compatibility/matrix.md) · [Idea library](docs/ideas/README.md)

## Acknowledgements

- [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2): the base mod this project builds on.
- [LDLib2](https://github.com/Low-Drag-MC/LDLib2): the UI framework.
- [NeoECO AE Extension](https://github.com/DancingSnow0517/NeoECOAEExtension): its LDLib2-based, AE2-styled GUI is the reference for ours.

The Federation Logic Processor texture is edited from AE2's Logic Processor texture and, like AE2's art, is licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), not AGPL-3.0.
