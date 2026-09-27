# Development CI artifacts

- User approved downloadable development JARs and asked about public-repository free-tier limits.
- `quick.yml` packages the exact binary named by `gradle.properties` after build/unit/archive checks, only on `refs/heads/dev`. It uploads a commit-labelled JAR, checksums, and build identity with 7-day retention. Uploaded diagnostic logs also retain 7 days.
- Upload precedes the full serial GameTest suite so a development build is available promptly. `BUILD.txt` and `docs/releasing.md` explicitly require checking the final workflow result for full GameTest status.
- The filename includes the Minecraft target and 12-character commit; artifact names add the run attempt to avoid same-run retry collisions. No sources, dependencies, runtime worlds, or broad build directories are uploaded.
- Standard public GitHub-hosted runners are free under the current billing documentation. Public artifact retention is 1–90 days; default 90. The historical GitHub staff answer to community discussion 26438 says public artifacts do not count toward the storage limit. Avoid presenting the Free plan's 500 MB allowance for private usage as a confirmed per-public-repository hard limit.
- References: https://docs.github.com/en/billing/concepts/product-billing/github-actions ; https://github.com/orgs/community/discussions/26438 ; https://docs.github.com/en/organizations/managing-organization-settings/configuring-the-retention-period-for-github-actions-artifacts-and-logs-in-your-organization
- Validation: actionlint on the changed workflow and local execution of its exact packaging script, followed by checksum and byte-equality checks against the Gradle output. Generated files stay under ignored `build/development/`.
