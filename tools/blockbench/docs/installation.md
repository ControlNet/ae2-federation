# Blockbench installation

Installed on 2026-09-26 for the current Linux x86_64 user.

- Version: 5.2.1, official stable GitHub release.
- Source: https://github.com/JannisX11/blockbench/releases/tag/v5.2.1
- Download: https://github.com/JannisX11/blockbench/releases/download/v5.2.1/Blockbench_5.2.1.AppImage
- SHA-256 (matches the official GitHub release asset digest):
  `abc3980ad1f1308a2352f7f920c57de8ff61fc2433ea8a64b81aa504c97987bf`
- Installation: `~/.local/opt/blockbench/5.2.1/`.
- Launcher: `~/.local/bin/blockbench`.
- AppImage extracted locally to avoid requiring a FUSE mount.
- Desktop entries: `~/.local/share/applications/blockbench.desktop` and
  `ae2-federation-blockbench.desktop`.
- MIME registration: `~/.local/share/mime/packages/blockbench.xml`.

No system Python packages or elevated installation were used. No plugins were
installed. The launcher does not disable Electron's sandbox.

Actual application startup and native project parsing were verified on an
isolated Xvfb display with software GL because the tool session has no DISPLAY.
The editor screenshot is a real Blockbench capture, not a Minecraft screenshot.
NeoForge lighting and transparency still require Minecraft validation.

The importer follows the official 5.2.1 project codec:
https://github.com/JannisX11/blockbench/blob/v5.2.1/js/formats/bbmodel.js
