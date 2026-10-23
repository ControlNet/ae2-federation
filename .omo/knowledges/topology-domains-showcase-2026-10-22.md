# Topology showcase across domains (2026-10-22)

The old gallery shot `ui-showcase-topology` (scenario `ui.mapping`) puts eight networks in one domain. At Fit its
cards are about 130 px wide on a 1600x960 window, too small to read, and the vanilla "Move with W, A, S and D"
tutorial toast covers the search box.

## Why the cards shrink

- Federation screens fix their GUI scale (`FederationGuiScale`), so a bigger window does not make cards bigger by
  itself. 1920x1080 still gives a larger canvas than 1600x960 (cards about 1.2x).
- In the "with connected domains" scope (`#graph_scope`), `DomainClusterLayout` puts domains in columns by hop
  distance from the opened domain. Every hop adds a column, so a chain of domains gets wide fast.
- A star (every domain one hop from the opened one) is three card columns wide: the opened domain's own networks,
  the shared networks, and the neighbours' own networks. Fit is then limited by height: each neighbour adds a row.

## Scenario `ui.showcase-domains`

`DomainsShowcaseScenario` (testmod, DEV_ONLY, group `ae2federation`, not in the CI name lists):

- A hub Switch with Main Base (west) and Storage Hall (top). Each neighbour in `Neighbour.ALL` gets a shared network
  on one hub face (an ME Chest against the hub, its creative energy cell against the neighbour's Switch three blocks
  out) and its own networks on the neighbour Switch's outer and clockwise faces. No Switches or networks touch, so
  every Switch is its own domain.
- A neighbour needs at least one network of its own. Otherwise the nested-domain rule folds it into the hub's plate.
- Rules: Main Base uses Storage Hall and every shared network. Each neighbour's own network uses the shared one's
  storage and runs on its energy.
- The scenario clears toasts and stops the tutorial, opens the hub Switch, switches to the related scope, presses Fit
  and selects Main Base. Shots: `ui-showcase-domains-focus` (opening camera) and `ui-showcase-domains` (Fit).

Measured at 1920x1080, Fit:

| Neighbours | Own networks per neighbour | Card width |
|---|---|---|
| 3 | 2, 1, 1 (1600x960) | about 145 px; the second own network hangs below, partly under the legend |
| 3 | 1 each | about 180 px |
| 2 | 1 each (committed) | about 245 px, as readable as the opening focus |

Run (about 1 minute):

```sh
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew --no-daemon :neoforge-1.21.1:runUiTestClient \
  -PfederationUiSelection=ui.showcase-domains -PfederationUiOutputDir=$PWD/build/ui-showcase-domains \
  -PfederationUiWindow=1920x1080 -PfederationUiGuiScale=3 -PfederationUiWatchdogSec=300
```

Images: `build/topology-showcase-check/` (`before-after.png`; `alt-4-domains/` holds the three-neighbour shot).
