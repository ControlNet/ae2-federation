# AE2 Lightning Tech Tianshu Supercomputer: two crafting paths (2026-10-24)

Checked by decompiling `ae2lt-2.1.1.jar` and `thunderbolt-2.0.1.jar` with Vineflower 1.10.1 (from the Gradle cache).

- **Ordinary jobs** run on Thunderbolt's CPUs. Thunderbolt's `ExtendedCraftingCpuServiceMixin` wraps AE2's
  `CraftingService.insertIntoCpus` and `getCpus`, so outputs reach these CPUs through `insertIntoCpus`, and our
  generic receipt hook (`655cc733`) sees them. A Matrix on another network, projected through a Router, works like
  any provider: `LightningTechCompatGameTests.tianshuOrdersFromMatrix` runs exactly that, planned by `thunderbolt:v2`.
- **Big-number orders** (`com.moakiee.ae2lt.crafting.big.BigCraftingService`, one per
  `TianshuSupercomputerControllerBlockEntity`, entered from the big confirm screen, `BigCraftConfirmMenuMixin`) only
  use `MatrixControllerBlockEntity`s on the controller's own Grid (`BigGraphExport.matrices(host.getGrid())`), take the
  inputs from storage into `job.escrow` first and call `matrix.acceptBigCrafting` directly. They never push through a
  projection and never wait for an output.

`docs/architecture/native-crafting-contract.md` (merged-Grid receipt limits) says this since `313536bd`; before it
claimed the whole Supercomputer never pushes through a projection.
