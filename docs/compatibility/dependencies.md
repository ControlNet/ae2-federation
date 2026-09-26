# Task 1 Dependency Qualification

## Qualified tuple

| Component | Candidate | Resolved declaration | Qualification |
|---|---:|---|---|
| Minecraft | 1.21.1 | NeoForge 21.1 line and exact mod range `[1.21.1]` | Qualified |
| Java | 21 | Gradle toolchain and compiler release 21 | Qualified |
| Gradle | 9.2.1 | Wrapper `gradle-9.2.1-bin.zip` | Qualified |
| ModDevGradle | 2.0.146 | `net.neoforged.moddev` plugin `2.0.146` | Qualified |
| NeoForge | 21.1.250 | ModDevGradle `neoForge.version` and exact mod range `[21.1.250]` | Qualified |
| Applied Energistics 2 | 19.2.17 | `org.appliedenergistics:appliedenergistics2:19.2.17` | Qualified |
| LDLib2 | 2.2.34 | `com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.34:all` | Qualified |

No candidate version was changed. All direct declarations are exact; there are no dynamic selectors or automatic toolchain downgrades.

## Compatibility intersection

- The official NeoForge 1.21.1 ModDevGradle MDK commit `30cafee9cd8d7f46427ec88fa8579d49c146df9a` pins ModDevGradle `2.0.146`, NeoForge `21.1.250`, Java 21, and Gradle `9.2.1` together.
- AE2 `19.2.17` is published for Minecraft `1.21.1`, requires NeoForge `21.1.169` or newer, and declares Java 21. Its runtime variant requires GuideME `21.1.1`.
- LDLib2 `2.2.34` publishes a Java 21 NeoForge 1.21.1 runtime variant and requires NeoForge `21.1.216` or newer. Its module metadata requires Kotlin stdlib `2.1.20`, Yoga `1.0.0`, and Taffy `1.1.4`.
- The project metadata declares exact runtime requirements for Minecraft, NeoForge, AE2, and LDLib2. Qualification applies only to this tuple and does not imply compatibility with another Minecraft target.

## Artifact integrity

Published Gradle module metadata reports these direct runtime artifact SHA-256 values:

| Artifact | SHA-256 |
|---|---|
| `appliedenergistics2-19.2.17.jar` | `460d779a0609b81409907d9956de8f6f70a1b0912257e3e5c3c7e75ac9630e95` |
| `ldlib2-neoforge-1.21.1-2.2.34-all.jar` | `5314e624e3b4258812a881b3a52886158af53886e5fb96d34919bdf0d5a374d6` |
| `gradle-9.2.1-bin.zip` | `72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f` |

Gradle dependency verification metadata in `gradle/verification-metadata.xml` is authoritative for resolved build inputs. The `dependencyVerificationSelfTest` task creates a pristine local fixture, corrupts only a copied artifact inside `build/tmp`, verifies that a nested strict-verification build rejects it, retains the rejection log, and removes the task-owned artifact and isolated Gradle home.

## Provenance and licenses

| Dependency | Provenance | Published license information |
|---|---|---|
| NeoForge | `net.neoforged:neoforge:21.1.250` from NeoForged Maven | LGPL 2.1 in its POM |
| ModDevGradle | `net.neoforged.moddev:net.neoforged.moddev.gradle.plugin:2.0.146` from NeoForged Maven / Gradle Plugin Portal | NeoForged project source and publication metadata |
| AE2 | `org.appliedenergistics:appliedenergistics2:19.2.17` from Maven Central | LGPLv3, MIT, and CC BY-NC-SA 3.0 components in its POM |
| LDLib2 | `com.lowdragmc.ldlib2:ldlib2-neoforge-1.21.1:2.2.34:all` from FirstDark snapshots Maven | LGPL-3.0 in upstream source |

This repository remains AGPL-3.0-only. Task 1 links to published dependencies and copies no third-party source.

LDLib2 `2.2.34` is checksum-identifiable in its official Maven publication, but the inspected public repository does not expose a matching `2.2.34` source tag. This is recorded as a provenance limitation, not hidden as source-tag equivalence.
