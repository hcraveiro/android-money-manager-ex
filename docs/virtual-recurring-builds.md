# Virtual recurring transaction Android test builds

The `Virtual Recurring - Android CI` workflow runs on pushes to
`feature/virtual-recurring-transactions` and pull requests against `master`.
It runs `testFdroidDebugUnitTest` and builds `assembleFdroidDebug`.

The debug variant uses `applicationIdSuffix ".virtualdebug"` and the
launcher label **MMEX Virtual (Dev)**. This lets it coexist with the normal
F-Droid Money Manager Ex application without replacing that installation.
The test APK is signed with the standard Android debug key; production
signing secrets are not required.

Successful workflow runs upload the APK as a GitHub Actions artifact for
14 days. The existing `release.yml` workflow is left unchanged and
does **not** run for this feature branch or its `virtual-v*` tags.

To publish a prerelease **only when ready**, push a tag matching
`virtual-v*` (for example `virtual-v0.1.0-beta.1`) pointing at the
desired feature commit. The dedicated workflow then builds the tagged
commit and publishes its **debug APK** as a GitHub prerelease. It never
publishes to Google Play, does not require production signing credentials,
and does not generate stable releases.

Important:
- Use test data or a backup, not your live financial database.
- The desktop and Android database schema versions must be aligned before
  testing shared version-22 database files.
- `workflow_dispatch` does not appear on GitHub Actions until the workflow
  exists on the repository's default branch. Feature-branch push triggers
  work independently of manual dispatch.
- If there are no workflow runs, check whether GitHub Actions is enabled
  for the fork in repository Settings.
