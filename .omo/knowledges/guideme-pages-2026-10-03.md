# GuideME pages in AE2's guide (2026-10-03)

The user chose to add the in-game guide to AE2's own guide rather than ship a separate one.

## How it is wired

- GuideME 21.1.1 comes with AE2 19.2.17 as a required dependency. AE2 builds its guide with
  `Guide.builder(ae2:guide).folder("ae2guide")`. `GuideReloadListener.loadPages` lists `ae2guide/` in **every**
  namespace, so `assets/ae2federation/ae2guide/**/*.md` joins AE2's guide with page ids `ae2federation:<path>`. No
  Java code and no compile dependency on GuideME are needed.
- `index.md` has `navigation` without `parent`, so it becomes the top-level "AE2 Federation" entry (position 900,
  Router icon). Every other page uses `parent: index.md`. A parent is resolved against the namespace root, not
  relative to the page, so pages under `items/` also write `index.md`.
- Item pages list `item_ids`, which is what lets players hold the guide key over an item to open its page. They also
  use AE2's own `categories` (`devices`, `network infrastructure`, `misc ingredients blocks`), so they appear in AE2's
  "Items, Blocks, and Machines" index too.
- Write every id with its namespace (`ae2:inscriber`, `ae2federation:router`). A bare id would resolve against the
  page's namespace. Links into AE2 pages use `ae2:ae2-mechanics/channels.md`.
- Translations live in `_zh_cn/<same path>`. GuideME loads them under the English page id, so relative links are
  identical in both languages.
- `<RecipeFor>` renders our crafting recipes and the `ae2:inscriber` recipe; AE2 registers the Inscriber recipe type
  with GuideME. There are no structure scenes yet.

## Chinese text: GuideME breaks lines only at whitespace

`guideme/layout/flow/LineBuilder.iterateRuns` treats only `Character.isWhitespace` as a break opportunity. A forced
mid-word break resets `lastBreakOpportunity` to 0, and a later overflow in the same run then emits an empty run. The
result: a CJK run longer than a line can spill past the right edge. A soft line break in the source becomes a space,
which turns into the only break point, so lines end early.

The Chinese pages therefore follow three rules:

- Each paragraph or list item is one source line.
- There is no space between CJK text and ASCII words or `<ItemLink>` tags.
- There is one space after each `，。；：！？` that is followed by more text, as a break point.

Put punctuation outside bold, `**粗体**。`. With `**粗体。**文字`, CommonMark does not treat the closing `**` as
closing, because it is preceded by punctuation and followed by a letter.

## Checks

- `GuidePagesContractTest` (JUnit) checks the following, and fails on a broken link or an unknown id (mutation
  checked 2026-10-03):
  - Every page has a Chinese copy, and the copy's frontmatter differs only in its title.
  - Every page except the entry has `parent: index.md`.
  - Relative links resolve.
  - Ids are qualified and known.
  - Each Federation item has exactly one page.
  - Chinese paragraphs and list items stay on one source line, with a space after their punctuation (mutation
    checked).
- Getting Started describes the screen as it is: a pair's rule switches appear on the right after you click the line
  between two network cards, or select one card and then the other network in its list. This was checked against the
  Task 33 screenshots `79_ui-graph-link-pair-selected` and `178_ui-graph-network-detail`.
- The `guideClient` run opens AE2's guide on a page at the title screen. It points
  `guideme.ae2.guide.sources` at our folder, so the pages hot-reload while it runs. `guideme.validateAtStartup` compiles
  only those development pages, and the log lists each "Compiling ae2federation:..." line plus any `PageCompiler`
  warnings.

```sh
./gradlew :neoforge-1.21.1:runGuideClient                                  # opens ae2federation:index.md
./gradlew :neoforge-1.21.1:runGuideClient -PguidePage=ae2federation:items/router.md -PguideLanguage=zh_cn
# Headless screenshot: start it under xvfb-run (1600x960 window, GUI scale 2), then
#   DISPLAY=:N XAUTHORITY=<xvfb-run's Xauthority> import -window root shot.png
```

Checked 2026-10-03 under Xvfb: all 11 pages compiled with no GuideME warnings. Getting Started (en) shows the
Inscriber, Bridge, 16-Cable and 4-Router recipes. The Router item page and the Chinese Mechanics, Remote Processing
and Troubleshooting pages wrap inside the page.

## Not done yet

- Annotated structure scenes (Bridge between two networks, Routers and Cable, a remote-processing setup) need `.snbt`
  structure files.
- The first-connection walkthrough has not been played through in a survival world.
