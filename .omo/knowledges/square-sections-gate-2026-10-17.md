# Square cross-sections only: a gate, not a removal (2026-10-17)

The owner wanted bundles limited to 2x2 and 3x3 without deleting code, so 1x2, 1x3 and 2x3 can come back easily.

- One switch: `CableCrossSection.BUILDABLE` (1x1, 2x2, 3x3); `CableCrossSection.buildable()` and
  `BundleShape.buildable()` read it. Adding a section back to the set restores both its bundle and its converter.
- Two checks:
  - `BundleSelection.crossSection` / `box` (public, used by the server's `BundleInteraction` and the client
    preview `BundleSelectionClient`) refuse with `NOT_BUILDABLE` ("Bundled cables are 2×2 or 3×3"). Package-private
    overloads take a `Predicate<BundleShape>`, so `BundleSelectionTest` still covers the 1xN plane-picking rule.
  - `BeamConverterShape` forms only buildable sections: 1x1x2, 2x2x4, 3x3x6.
- Everything else is unchanged and still works for non-square bundles: multipliers, rendering, branching, extension,
  Jade, saved bundles (a world's existing 2x3 keeps working), and `BundledCables.form` used by GameTests.
- A refused second click keeps the first corner (by design); right-click air with the wrench to start over. Capture
  scripts must reset between selections.
- In game: `build/square-sections-check/compare.png` (before: 2x3 accepted and formed, 1x2x4 converter formed; after:
  2x3 refused with the message, 2x2 forms, 1x2x4 "not a converter's size").
