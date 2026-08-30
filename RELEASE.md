# Release process

Releases are automated through GitHub Actions and publish every module to Maven Central under
`io.github.big-jared`.

## Prerequisites

Configure these secrets in GitHub Settings, Secrets and variables, Actions:

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`: Central Portal credentials
- `SIGNING_KEY`: the armored GPG signing key
- `SIGNING_KEY_ID`: the key id (last 8 characters)
- `SIGNING_KEY_PASSWORD`: the key password

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

## After a release

1. Verify the artifacts on https://central.sonatype.com (can take about 30 minutes)
2. Check the GitHub release and tag
3. Update dependent projects
