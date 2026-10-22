# Guide audit before the restructure (2026-10-22)

The state of the GuideME guide on dev (ec76bfed/e8a7f564) before the cleanup the owner asked for. The automation
pages (feature/function-system) are left out on purpose: the general part is cleaned up on dev first, then merged
into the feature branch.

## Inventory

- English: 28 pages, about 2,100 lines. 9 topic pages, 11 item pages, 8 example pages. `_zh_cn` mirrors every page.
- Navigation: every topic page and every item page has `parent: index.md`, so the sidebar is one flat list of 20
  entries. Topics use positions 10-40, items 100-150, examples sit under `examples/index.md`. `statistics.md` and
  `examples/index.md` share position 35.

## Correctness problems

- Operating power is missing from Getting Started. With the default `requireOperatingPower=true`, a domain whose
  blocks cost FE (Switch 2, Router 2, cable 0.5, Provider/Endpoint 2 FE/t) stops without a matching Beam Converter.
  `PowerLedger` lets a domain with no cost run, so a Bridge-only domain runs free. Getting Started's Switch + Cable
  route, and the scenes `switch_cable`, `switch_hub`, `network_capacity`, `remote_processing`, `router_segments` and
  `federation_p2p_tunnel`, have no converter: built as shown, those domains stop.
- "Power" means two things that the guide never separates: the ME power rule (AE energy shared between networks) and
  the domain's FE operating power (Beam Converter). Getting Started says "only one of them needs power" and never
  mentions FE.
- Nexus Core's "Used in" list misses Beam Former, Beam Converter and Quantum Cube. The index's item list misses the
  Federation P2P Tunnel.

## Structure problems

- Concepts live on item pages: segments and subdomains are explained in network-capacity, router, bundled-cables,
  beam_former, quantum_cube and beam_converter. Operating power exists only on the beam_converter item page. The
  "domain stops" consequence is restated on three pages.
- The Federation screen is spread over getting-started, mechanics, statistics and troubleshooting, with no page of its
  own.
- remote-processing.md is titled "Remote Crafting" and holds two unrelated mechanisms: crafting with another
  network's AE2 providers (a rule) and Federation Pattern Provider + Endpoint (no rule).
- The four ways to carry a domain over distance (cable, P2P tunnel, Beam Former, Quantum Cube) are never compared.

## Prose patterns to fix

- Denial-led sentences: "Federation does not replace AE2's own cables...; it only connects networks", "It is not an
  ME cable", "You do not need a Router to build a domain", "Neither rules nor power play a part".
- Sentences that pack four or five facts joined by colons and semicolons, especially on the capacity pages.
- Repeated consequence text ("its networks keep working on their own, but share no storage, crafting, processing or
  power").

## Decisions for the restructure (owner, 2026-10-22)

- Approved tree: index, Getting Started, mechanics/ (domains, rules + re-export incl. across domains, two kinds of
  power, capacity, bundled cables, long-distance links, Federation screen + statistics), crafting/ (AE2 providers via
  a rule; Federation Provider + Endpoint), items/, examples/, Troubleshooting.
- Every page must be reachable from the sidebar: no page without `navigation`, and every page hangs directly
  under one of the guide's roots (see the sidebar limit below).
- Beam Converters go into complete builds only (Getting Started and every example, the optional packs' too).
  Scenes that illustrate one concept (shapes, connections, bundles) stay as they are; the text says once that a
  domain needs FE.
- Keep the FE-only converter. The guide states it plainly: vanilla + AE2 produce no FE, so a Switch/Router domain
  needs another mod's generator, a Creative converter, or `requireOperatingPower=false`; a Bridge-only domain
  costs nothing and needs no converter.
- Contract constraints: `GuidePagesContractTest` (required pages, Chinese mirror with identical frontmatter except
  the title, every snbt shown, one owner per item id, links resolve, optional packs' pages included).

## GuideME sidebar shows two levels only (found 2026-10-22)

`GuideNavBar.recreateRows` (GuideME 21.1.1, and still on GuideME main) adds a row for each root node and for its
direct children, nothing deeper. Our `index.md` is already a root of AE2's guide, so a page whose parent is another of
our pages never appears in the sidebar: before the restructure the seven example pages were missing this way, and
after grouping into folders the mechanics, crafting, item and example pages would all have been. GuideME logs no
warning for it.

First fix (eb4ec28d): flatten everything under `index.md`. The owner then preferred sections that fold, so the guide
is now five roots of AE2's guide, each with its pages as direct children:

| Root page | Sidebar title | Position | Children |
|---|---|---|---|
| `index.md` | AE2 Federation | 900 | getting-started, troubleshooting |
| `mechanics/index.md` | Federation: How It Works / 联邦：工作方式 | 910 | rules ... statistics |
| `crafting/index.md` | Federation: Crafting / 联邦：跨网络合成 | 920 | ae2-providers, processing-endpoints |
| `items/index.md` | Federation: Items and Blocks / 联邦：物品与方块 | 930 | every item page |
| `examples/index.md` | Federation: Examples / 联邦：使用示例 | 940 | every example, the optional packs' too |

AE2's own roots use positions 0-50. Section pages list their pages by hand. `GuidePagesContractTest.ROOTS` and
`pagesHangDirectlyUnderARootAndChineseCopiesKeepTheirPlace` hold the rule. Root titles stay short enough for one
sidebar line ("Federation: Crafting Across Networks" wrapped).

A plain YAML value holding ": " (an English title like `Federation: How It Works`) makes GuideME log "Failed to parse
frontmatter" and drop the page from navigation, silently in the sidebar; quote such titles. The contract test checks
it. Screenshots: `build/guide-restructure-check/{before,after}-sidebar-*.png`.

## Guide screenshots without disturbing other clients

`runGuideClient` under a private Xvfb (`Xvfb :96 -screen 0 1600x960x24`), wait for `Compiling ae2federation` in the
Gradle output plus ~20 s, then drive it with XTEST (wheel button 5 to scroll, click the sidebar triangle at (14, 157)
to expand the Federation entry) and grab the root window with Xlib + Pillow. Close with WM_DELETE_WINDOW; Gradle exits
on its own, then stop the Xvfb by its literal PID. A pre-change build renders the same way from a detached
`git worktree` of the old commit.
