# Future ideas

This directory collects future feature ideas and their evolving product requirements. An accepted direction records
what the feature should do; it does not mean implementation has started or a release has been scheduled.

## Index

| Idea | Discussion status | Scope |
|---|---|---|
| [Federation P2P](federation-p2p.md) | Direction and core behavior confirmed | Native AE2 P2P mode carrying Federation connectivity, including cross-dimensional support in its first release |
| [Custom policies](custom-policies.md) | Direction established; details under discussion | Storage resource filtering, conditions based on AE2 information, and interchangeable graph/script editors |
| [Remote Federation connections](remote-federation-connections.md) | Multiple forms under exploration | Quantum-bridge-style jumping is the first candidate; other connection models remain open |
| [Survival playability](survival-playability.md) | Early availability, five recipes and GuideME documentation confirmed; cable recipe open | Obtainable basic Federation connections early in AE2 progression, with crafting, setup workflows and an AE2-style in-game guide |

The remote connection page also carries the earlier "Federation quantum bridge" working-name discussion.
Its name, appearance and detailed behavior remain undecided.

## Comparative research

The 2026-10-03 research is organized by idea. Following the user's clarification, P2P and policy each have their
own 100-project catalog; projects may overlap, but their relevance is evaluated separately for each idea.
The two catalogs share 93 projects and cover 107 distinct projects. Each contains 92 projects with approximately
one million or more lifetime downloads on an inspected platform, plus eight explicitly labeled smaller specialists.

| Idea | Research record |
|---|---|
| Federation P2P | [100-project P2P comparison](../../.omo/knowledges/federation-p2p-mod-survey-2026-10-03.md) |
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
