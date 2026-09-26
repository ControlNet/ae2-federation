# Task 3 Independent Re-verification

- Persisted UI evidence must be semantically re-parsed after artifact hash verification; hashes
  alone only establish consistency of a rebound artifact set.
- Reuse the same LDLib2 semantic verifier for producer-time and persisted consumption, then
  compare all outer projections with the verified upstream report.
- The decisive regression probes are fully rebound zero-check, malformed-upstream, and forged
  acknowledgment attempts. Each must fail after paths, current identities, timestamps, and
  artifact hashes have been recomputed.
- LDLib2 report-level environment metadata precedes scenario GUI-scale changes. Render-step
  attachments and captured pixel dimensions are authoritative for the tested rendered state.
- Independent confirmation used fresh attempt `attempt-20260913T173814864Z`; canonical
  consumption passed and all three rebound semantic attacks failed closed.
