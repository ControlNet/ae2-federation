# Federation switch — archived discussion

Archived: 2026-10-10, after the owner confirmed the idea was implemented.

See [implemented Federation Switch](../../features/federation-switch.md) for current behavior. The discussion below
is historical: its status line, "planned" wording and open details reflect the stage before implementation. The
details it left open were decided as follows: the Switch copies the Router's six-face layout and domain screen, takes
the Router's former recipe, and has no art yet.

[Idea index](../../ideas/README.md).

Discussion date: 2026-10-10.
Status: preliminary feature idea; not implemented.

## Purpose

Introduce a Federation switch to separate ME network attachment from Federation interconnection.

## Proposed responsibilities

- The switch connects ordinary ME networks to the Federation.
- Routers handle Federation-side interconnection.
- In this future design, ordinary ME networks must attach through switches; routers no longer connect
  directly to ordinary ME networks.

## Planned registry IDs

| Entry | Planned ID |
|---|---|
| Switch block | `ae2federation:switch` |
| Switch block item | `ae2federation:switch` |

This follows the existing short registry paths `ae2federation:router` and `ae2federation:cable`.
The block and its inventory item share the same resource location in their respective registries.
These IDs are planned here; no registration has been implemented.

## Details left open

The switch's connection layout, appearance, recipe, and implementation remain undecided.
This page records only the initial device role. It does not specify multiblock expansion, cable capacity,
subdomains, power requirements, or configuration behavior. Existing implementation is unchanged.
