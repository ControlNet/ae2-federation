# A BlockCapabilityCache must be read to fire again (2026-10-13)

NeoForge's `BlockCapabilityCache` calls its invalidation listener only once per read: after an invalidation it marks
itself stale and stays silent until `getCapability()` is called again. A port that skips reading the cache while it is
"closed" never hears the next invalidation.

Found with bundled cables. `CableFacePort.resolve` returned early when the face's `open` predicate (the bundle
contact rule) was false, before `cache.getCapability()`. A bundle beside half a Router body therefore stayed unlinked
after the body was completed: the Router side relinked, the cable side never resolved again
(`BundledCableGameTests.bundleLinksThroughWholeEndFaces`, "Waiting for the completed body to link").

Rule: read the cache first, then apply any gate. The same applies to every port that wraps a cache (`RouterFacePort`
reads it on every resolve already).

Related: a Router body changes its blockstate with `UPDATE_KNOWN_SHAPE` and no neighbour updates, so nothing tells the
cables beside it. `RouterBlockEntity.setBlockState` now invalidates the capabilities of its own position and of its six
neighbours whenever the state changes, which re-arms both sides' caches.
