# Browser warning on development artifact

The user reported a browser virus warning for
https://github.com/ControlNet/ae2-federation/actions/runs/37100338421 .
Browser name, exact warning text and any antivirus detection name are still unknown. Do not label it a confirmed
false positive or malware incident on this evidence alone.

Follow-up: the user reports both Zen (Firefox-based) and Chrome block the download. Exact wording, operating system
and any antivirus verdict remain unknown. Firefox documentation describes Google Safe Browsing metadata checks;
Chrome also uses Safe Browsing. Therefore cross-browser blocking does not necessarily represent two independent
antivirus detections. Zen's actual configuration has not been inspected. Source:
https://support.mozilla.org/en-US/kb/how-does-phishing-and-malware-protection-work

Further follow-up: the OS is Windows and the user found no corresponding Windows Defender protection-history event.
This strengthens, but does not prove, the browser-protection hypothesis. No exact warning text or Safe Browsing
verdict is available yet. Comparing the same run's text-only `required-gametest-log` artifact with the mod artifact
could help distinguish broader download-source blocking from file-specific blocking, but cannot prove either cause
or establish safety. No change to antivirus settings, packaging or distribution was made.

Read-only GitHub inspection identified commit `76a4237695091644dcef8cc44cd48d7e52115f87` and successful Quick correctness.
Development artifact id: `11265980951`; name: `ae2federation-neoforge-1.21.1-dev-76a423769509-1`.
Downloaded through the authenticated GitHub API to temporary storage for static ZIP inspection only.

- ZIP SHA256: `081923377da68787a3ad84d0ca6dd502fcccedf6fd79a20f1a9b12f8fa4a0ca2`, matching GitHub artifact metadata.
- JAR SHA256: `f9c6e4df88672308ac5faf9f598d6fe8fcd37ed717f35631e601bb247d3d982f`, matching bundled SHA256SUMS.txt.
- Outer ZIP contains BUILD.txt, SHA256SUMS.txt and the development JAR only.
- JAR contains 958 entries under mod classes, assets, data and metadata; ZIP CRC validation passes.
- No nested JAR, exe, dll, so, bat, cmd, ps1, sh, vbs or js entries, or traversing/absolute entry paths found.
- Limited class-string screening found no ProcessBuilder, java/net/URL, java/net/http, java/net/Socket, cmd.exe,
  powershell or ClassLoader markers. Such screening is not comprehensive malware analysis.

During the initial inspection no JAR was executed, no third-party scan service received the file, and no antivirus
scan was performed. The subsequent local-container antivirus scan is recorded below.
CI test success and matching hashes establish consistency with the published build, not absence of malware.
The browser warning may concern reputation, a malware verdict or local antivirus integration; exact text is needed.
Official references:
https://support.google.com/chrome/answer/2898334
https://learn.microsoft.com/en-us/windows/security/operating-system-security/virus-and-threat-protection/microsoft-defender-smartscreen/

## Deeper artifact and source investigation

User supplied exact Zen text: `This file contains a virus or malware.` It is not merely an uncommon-download
warning. The user requested investigating the suspected false positive. No browser verdict internals were available.

Rebuilt the checked-out CI commit (HEAD exactly `76a4237695091644dcef8cc44cd48d7e52115f87`) with:

```sh
./gradlew :neoforge-1.21.1:jar --rerun-tasks --dependency-verification=strict --no-configuration-cache --console=plain
```

Build passed. All 882 non-directory JAR entries, including all 525 classes, match the downloaded CI artifact byte
for byte. The entire rebuilt JAR also has SHA256
`f9c6e4df88672308ac5faf9f598d6fe8fcd37ed717f35631e601bb247d3d982f`.
No evidence of CI-specific additions or bytecode replacement was found. This establishes reproducibility against
the local source/toolchain, not an independent audit of every dependency or possible behavior.

Parsed class constant pools and inspected relevant source. Sensitive references found were:

- Reflection in NativeStorageAliasProbe: reading instance fields to identify storage-wrapper aliases.
- Base64 in FederationDomainGraphSnapshot and FederationMenuAuthority: game UI/domain serialization.
- Files.readAllBytes in NeoForgeEntrypoint: hashing the mod's own JAR when the opt-in artifact-proof flag is enabled.

The screened class references contained no direct JDK network client/socket, process execution, native-library
loading or custom ClassLoader references. No URL string constants were found by the class audit. String occurrences
of `java/lang/Runtime` were RuntimeException, not Runtime.exec. These checks are bounded static analysis and do not
prove absence of indirect behavior through dependencies. There is no evidence any of these ordinary calls caused
the browser verdict; do not delete them speculatively.

## Independent antivirus scan

Used the official ClamAV container, pinned to digest
`sha256:ebec5bc138401b36ae987caa1a3fa3c3b2a21ed3d51f0bfa5852825e663e67b0`.
FreshClam updated the daily database to 28141 (2026-10-02). Engine: ClamAV 1.5.4; signatures: 3,628,114.
The scan ran as the container's clamav user with dropped capabilities and a read-only sample mount. Network access
was used for signature updates; no sample was uploaded to a scanning service. The JAR was never executed.

Both `/scan/artifact.zip` and `/scan/ci.jar` returned OK; exit code 0; scanned files 2; infected files 0;
data scanned 10.72 MiB. Verbose output confirms traversal of nested JAR class/resource entries.
Initial setup attempts failed on permissions and are not counted as successful scans. FreshClam's missing-clamd
notification is irrelevant to the subsequent standalone clamscan, which completed successfully.

Temporary evidence: `/tmp/ae2-ci-artifact-audit-bXenak/build-comparison.json`,
`class-sensitive-references.json`, and `clamav-scan-current.log`. This written summary preserves the key results
if temporary files are removed. No runtime source, packaging rules or browser security settings were modified.

Conclusion: reproducible expected artifact and no ClamAV detection support a suspected false positive, but neither
identifies the proprietary browser rule nor overrides its verdict. To establish the actual browser decision,
open `chrome://safe-browsing/` before reproducing the blocked download and inspect Download Protection. Request
only sanitized response/verdict fields, not full request logs, tokens or signed artifact URLs.
Official diagnostic guidance: https://chromium.googlesource.com/chromium/src/+/master/docs/security/faq.md

## Browser verdict localized

The user supplied Chrome's Download Protection diagnostics. Only these sanitized findings are retained; signed
Azure download URLs, response tokens and browsing/profile metadata must not be copied into project documentation.

- Download URL-chain check: `SAFE`.
- Archive parser: `VALID`, three files, zero directories, not encrypted.
- Outer classification: `ZIPPED_EXECUTABLE`.
- Archived JAR classification: `WIN_EXECUTABLE`, executable flag true, length 1,327,263 bytes.
- ZIP length: 1,167,400 bytes.
- ClientDownloadResponse: `DANGEROUS`, not `UNCOMMON` or `DANGEROUS_HOST`.
- Decoding the submitted Base64 SHA256 digests reproduces exactly the ZIP and JAR hashes already inspected above.

This identifies the observed block at the remote download-protection verdict stage, after a safe URL-chain result.
It does not reveal which input feature, signature, model, hash reputation or source signal caused the backend verdict.
Do not claim that the JAR hash alone is blacklisted, or that URL-related signals cannot affect the later verdict.

Current Chromium source lists `.jar` with `FULL_PING` in download_file_types.asciipb. GetDownloadType has a fallback
return of WIN_EXECUTABLE with a TODO for a separate unspecified/default value. Thus the JAR label is not evidence
of a hidden native Windows EXE or, by itself, a browser classification bug. The verdict enum separately distinguishes
DANGEROUS, UNCOMMON and DANGEROUS_HOST. Inspected current upstream source, not an exact build checkout of Chrome 154.

Primary sources:
https://chromium.googlesource.com/chromium/src/+/main/chrome/common/safe_browsing/download_type_util.cc
https://chromium.googlesource.com/chromium/src/+/main/components/safe_browsing/content/resources/download_file_types.asciipb
https://chromium.googlesource.com/chromium/src/+/main/components/safe_browsing/core/common/proto/csd.proto

Current assessment: suspected false positive in download protection, supported by identical local/CI builds and
negative ClamAV results. The proprietary detection reason is not exposed by this response. A provider review would
be required to confirm/correct its verdict. No appeal was submitted and no sample was uploaded by the assistant.

## Intermittent behavior reported

The user reports another/new CI artifact can download normally and the block is intermittent. This is evidence
against treating every build of the mod as persistently blocked, but does not identify a cause. At the follow-up
query, the latest visible run remained 37100338421; the successful-download run/artifact was requested explicitly.
Do not label an arbitrary earlier run as the known-good sample. Compare inner JAR bytes, outer ZIP bytes and
sanitized verdict fields once that sample is identified. A new run changes artifact identity/download metadata;
whether either file hash changes must be measured, not assumed. Backend timing/cache/reputation changes remain
hypotheses. Do not modify code or repack artifacts solely to try to evade the warning.

## Known-normal versus flagged artifact comparison

The user identified run 37100165038 (commit `af128275166df5d30bd7fdf61f24654d8ed207dc`) as downloading normally.
It predates the flagged run, rather than being a newer rerun of the same commit. Artifact id 11265214732 was
downloaded read-only through GitHub API and its ZIP hash matched GitHub metadata; its JAR matched bundled checksums.

| Property | User-reported normal | User-reported blocked |
|---|---|---|
| Run | 37100165038 | 37100338421 |
| ZIP bytes | 1,164,628 | 1,167,400 |
| JAR bytes | 1,324,356 | 1,327,263 |
| Non-directory JAR entries | 881 | 882 |
| Java classes | 525 | 525 |

Normal ZIP SHA256: `3636f6bef7881579be8a326caff5fd5876784c28c305b31b84ca53ddc8c24c94`.
Normal JAR SHA256: `313290fd414558ee496bf4d8424df720a73d528ded4ab4cafeea26b32cf343b1`.
All 525 Java class files are byte-identical between the two artifacts. All common non-guide files also match.
Exactly twelve existing English/Chinese GuideME Markdown pages changed, and one structure resource was added:
`assets/ae2federation/ae2guide/assets/native_projection.snbt`. No files were removed. Source diff agrees with these
changes. The added structure describes ordinary AE2 blocks and no entities; the principal Markdown addition explains
native cross-network crafting with GameScene, ImportStructure and annotations. No external script or command payload
was found in the reviewed additions.

Evidence: `/tmp/ae2-ci-artifact-audit-bXenak/normal-versus-flagged.json`.
Conclusion: opposite reported download outcomes occur for identical executable class bytes but different resource
content, JAR/ZIP hashes and artifact addresses. This strengthens the suspected false-positive assessment and provides
no evidence for deleting reflection/mixins or changing Java code. It does not establish which resource, hash or
download-context signal caused the backend verdict; specifically, do not claim GuideME text itself is the trigger.
The samples do not demonstrate intermittent decisions for the exact same file and URL.
