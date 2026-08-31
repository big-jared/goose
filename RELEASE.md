# Release process

Releases are automated through GitHub Actions and publish every module to Maven Central under
`io.github.big-jared`.

## Prerequisites

Configure these secrets in GitHub Settings, Secrets and variables, Actions:

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`: Central Portal credentials
- `SIGNING_KEY`: the GPG signing key, either armored text or its base64 (one line)
- `SIGNING_KEY_PASSWORD`: the key password
- `RELEASE_PUSH_TOKEN`: a fine-grained PAT (this repo, Contents: read and write) belonging to
  an admin. Needed because main's ruleset requires CI checks and the Actions bot cannot bypass
  it on a user-owned repo; the release workflow pushes the version bump and tag with this token.

No key id is needed: the publish convention configures the in-memory key without one (Gradle's
id lookup cannot match keys with nonzero high id bits, and a single-key ring needs no id).

## Releasing

**Option 1, PR with the `release` label (patch bumps).** Create a PR, add the `release` label,
merge it. The workflow computes the next patch version, runs the full test suite, bumps
`VERSION_NAME` in `gradle.properties` and the coordinates in the README, tags `vX.Y.Z`, creates
a GitHub release with a changelog from git history, and publishes to Maven Central.

**Option 2, manual dispatch (minor/major bumps).** Actions, Create Release, Run workflow, enter
the version explicitly. Same steps, your version.

## Versioning

Semantic versioning: major for breaking changes, minor for backwards-compatible features, patch
for fixes. The label flow bumps patch; use manual dispatch for anything bigger.

## Local verification

`./gradlew publishToMavenLocal` publishes all six modules to `~/.m2` with no credentials and no
signing. Signing only engages when the release workflow passes `goose.releaseSigning=true`.

## CI policy

Everything runs on standard `ubuntu-latest` runners, which are free with unlimited minutes on
public repositories: no personal-card exposure at any usage level (larger runners or a private
repo would change that; neither is used). The fast `build` job (unit + Robolectric + apiCheck +
assemble) runs on every push and PR. The two emulator jobs (`instrumented`, `maestro`) run on
main pushes and manual dispatch, and on PRs only when the `emulator` label is applied.

Merging to main is governed by the "main requires CI" ruleset: the `build`, `instrumented`,
and `maestro` checks must pass (a check skipped by the label gate counts as satisfied, so
unlabeled PRs need `build` only). Repo admins bypass the ruleset for direct pushes; force
pushes and branch deletion are blocked for everyone.

## Snapshots

Every push to main can publish `<version>-SNAPSHOT` of all modules to the Central Portal
snapshots repository. Two one-time steps: enable SNAPSHOT publishing for the
`io.github.big-jared` namespace at central.sonatype.com (namespace settings), then set the
gate variable:

```bash
gh variable set CENTRAL_SNAPSHOTS_ENABLED -b true -R big-jared/goose
```

Until then the snapshot job skips cleanly.

## After a release

1. Verify the artifacts on https://central.sonatype.com (can take about 30 minutes)
2. Check the GitHub release and tag
3. Update dependent projects
