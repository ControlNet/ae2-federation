# Configurable gameplay rules for modpacks

Discussion date: 2026-10-10.
Status: user-confirmed controls and enabled-by-default direction; numerical defaults and schema remain open.

## Intent

The default configuration should expose the full intended gameplay as future extensions are implemented.
Operating-power requirements and cable capacity restrictions are enabled by default. Pack authors may
explicitly disable them when intentionally adapting the experience. Configuration provides opt-outs, not
an expert-mode gate hiding the intended gameplay. No separate standard/expert preset system is selected.
This supersedes the earlier assistant recommendation to default both requirements off. It does not require
implementing all ideas at once or enabling every future experimental or mutually exclusive option.

The source audit on this date found no production mod configuration registration or custom configuration
file reader. Saved network Policy settings and artifact-proof JVM flags are separate concerns. See the
[audit](../../.omo/knowledges/configuration-support-audit-2026-10-10.md).

## Confirmed controls

| Control | Default / applicability | Related idea |
|---|---|---|
| Require Federation operating power | Enabled by default; independently disableable | Operating power |
| Enforce Federation cable capacity | Enabled by default; independently disableable | Multiblock and cable capacity |
| Base capacity of one cable | Used only while cable capacity enforcement is enabled; value undecided | Multiblock and cable capacity |
| Operating energy costs | Used only while operating-power requirements are enabled; values undecided | Operating power |

Disabled parent rules make their numerical settings inactive; they do not require removing those settings
from the file. File format, option IDs, numerical defaults, cost granularity, and reload behavior remain
implementation/design choices. Separate per-cross-section overrides are not confirmed requirements.

## Additional recommendations, not confirmed requirements

- Keep multiblock construction, bundled cable appearance, and connection functionality available when
  capacity restrictions are disabled. Disabling restrictions should not remove registered blocks or items.
- Separate capacity accounting from existing Policy permissions and operating energy from ME power sharing.
- Explain the affected gameplay and dependent numerical settings in configuration comments so pack authors
  can make deliberate choices. No warning dialog, permission gate, or author-only access restriction is proposed.

## Ownership and lifecycle recommendations

Gameplay rules should be authoritative on the server, including the integrated server in single-player.
Clients should display the effective rules needed for capacity and power diagnostics; visual preferences
can remain client-local. Pack authors need a documented way to distribute defaults for new worlds and
understand their effect on existing worlds. Configuration location and platform API are implementation choices.

Prefer explicit restart/reload requirements for topology-affecting rules rather than promising unrestricted
hot reload. Enabling constraints in an existing world should recompute status and report overload or missing
power without deleting infrastructure. Specify behavior for pending operations before supporting transitions.
Recipes and progression adjustments can be considered separately from runtime rule toggles.

## Related ideas

- [Multiblock routers, switches, and cable capacity](multiblock-routing-and-cable-capacity.md)
- [Optional operating power](federation-operating-power.md)
- [Line-of-sight beam links](line-of-sight-beam-links.md)
- [Remote connections](remote-federation-connections.md)

This idea does not introduce configuration files, option IDs, runtime behavior, or implementation commitments.
