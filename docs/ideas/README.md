# Future ideas

This directory collects future feature ideas and their evolving product requirements. An accepted direction records
what the feature should do; it does not mean implementation has started or a release has been scheduled.

## Index

| Idea | Discussion status | Scope |
|---|---|---|
| [Lightweight Jade cable capacity display](jade-cable-capacity.md) | Preliminary; depends on future capacity rules | A concise cable network-count/limit line; no broad diagnostic HUD |
| [Configurable gameplay rules](configurable-gameplay-rules.md) | Enabled-by-default controls confirmed; schema and values open | Full intended gameplay by default; independent power/capacity opt-outs and dependent numerical settings |
| [Custom policies](custom-policies.md) | Direction established; details under discussion | Storage resource filtering, conditions based on AE2 information, and interchangeable graph/script editors |
| [Remote Federation connections](remote-federation-connections.md) | Multiple forms under exploration | Quantum-bridge-style jumping is the first candidate; other connection models remain open |
| [Optional Federation operating power](federation-operating-power.md) | User-proposed direction; details open | Configurable power requirement, an energy-input device, and different costs for different Federation blocks, especially wireless links |
| [Line-of-sight beam links](line-of-sight-beam-links.md) | User-proposed connection form; details open | Long-distance straight-line links without intervening cable, requiring an unobstructed path and increased operating energy |
| [Multiblock routers, switches, and cable capacity](multiblock-routing-and-cable-capacity.md) | Region-counting candidate; concentrated layouts accepted; balancing open | Solid-cuboid routers and switches, bundled cable multipliers, and attachment capacity independent of Policy; no router capacity limit for now |

The remote connection page also carries the earlier "Federation quantum bridge" working-name discussion.
Its name, appearance and detailed behavior remain undecided.

## Implemented ideas

- [Nexus Processor and Nexus Core](../features/survival-playability.md): implemented the Printed Nexus Circuit ->
  Nexus Processor -> Nexus Core chain; Federation devices consume cores. The
  [original discussion](../archive/ideas/nexus-processor.md) is archived for historical context.
- [GuideME use-case tutorials](../features/guideme-use-cases.md): implemented bilingual examples and optional-mod tutorial packs.
  The [original discussion](../archive/ideas/guideme-use-cases.md) is archived for historical context.
- [Survival playability](../features/survival-playability.md): implemented recipes, processor icon and GuideME guide.
  The [original discussion](../archive/ideas/survival-playability.md) is archived for historical context.
- [Federation P2P](../features/federation-p2p.md): implemented a native AE2 P2P mode that carries Federation
  connectivity, including across dimensions. The [original discussion](../archive/ideas/federation-p2p.md) is
  archived for historical context.
- [Federation Switch](../features/federation-switch.md): implemented the Switch, which attaches ME networks, and made
  the Router Federation-only. The [original discussion](../archive/ideas/federation-switch.md) is archived for
  historical context.

## Comparative research

The 2026-10-03 research is organized by idea. Following the user's clarification, P2P and policy each have their
own 100-project catalog; projects may overlap, but their relevance is evaluated separately for each idea.
The two catalogs share 93 projects and cover 107 distinct projects. Each contains 92 projects with approximately
one million or more lifetime downloads on an inspected platform, plus eight explicitly labeled smaller specialists.

| Idea | Research record |
|---|---|
| Federation P2P (implemented) | [100-project P2P comparison](../../.omo/knowledges/federation-p2p-mod-survey-2026-10-03.md) |
| Custom policies | [100-project policy comparison](../../.omo/knowledges/custom-policy-mod-survey-2026-10-03.md) |
| Remote Federation connections | [100-project wireless/remote survey](../../.omo/knowledges/wireless-tech-mod-survey-2026-10-03.md) |

These are comparative samples with sources and version boundaries, not popularity rankings or claims that every
project implements the proposed feature. The idea pages collect the resulting player-facing suggestions while
the research records retain detailed evidence and explicitly marked screening results.

## Where information belongs

- `docs/ideas/`: player intent, confirmed product requirements, candidate features, open questions and references.
  Maintain the current product discussion here rather than adding it to a growing research log.
- [DESIGN.md](../../DESIGN.md) and `docs/architecture/`: design and architecture contracts maintained as features
  enter concrete design or implementation. An idea entry alone does not rewrite these documents.
- `.omo/knowledges/`: source investigations, compatibility findings, implementation history and verification evidence.
  Link relevant evidence from an idea instead of copying technical investigations into it.

## Maintaining an idea

Use one descriptively named Markdown file per substantial idea and add it to the index. Keep its purpose, confirmed
requirements, proposals and open questions clearly separated. Discussion dates belong inside the page; filenames
remain stable as the idea evolves. Split a related idea when it develops enough independent scope to warrant a page.

Record new decisions in the idea page. Label assistant suggestions as proposals until the user accepts them.
Examples illustrate behavior; they do not silently choose limits, syntax, names or launch scope. Concrete technical
choices remain for the future coding agent unless explicitly decided during discussion.

When implementation begins, link the resulting design or implementation evidence and update the status. Retain
the idea's history and links rather than presenting a future proposal as an already available feature.

When an idea is implemented, move its discussion to `docs/archive/ideas/`, document current behavior in
`docs/features/`, and link both from the implemented-ideas section. Historical proposals are not current requirements.
