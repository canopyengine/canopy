# Preparing a Maven Central prerelease

This workflow prepares the proposed `0.1.0-alpha.1` artifacts under verified
namespace `io.github.canopyengine`, including plugin ID
`io.github.canopyengine.compiler`. It does not declare stable 0.1.0 or automatically
publish to Central. Kotlin packages remain unchanged. Follow the matching pinned
consumer installation instructions in canopy-docs.

## One-time owner setup

Create a GitHub environment named `maven-central` in the engine repository and
configure required reviewers where available. Restrict its deployment branches
to the release branch/main as appropriate. The workflow runs only from `main`; merge the reviewed preparation PR
before dispatching it. If required-reviewer protection is unavailable for your account, the
manual workflow dispatch and manual Portal publication remain separate deliberate
steps; do not assume the environment name alone supplies approval protection.

Set these environment or repository secrets in GitHub settings:

| Secret | Value |
| --- | --- |
| `CENTRAL_USERNAME` | Username from the Central publishing token, not account login |
| `CENTRAL_PASSWORD` | Password from that publishing token |
| `MAVEN_SIGNING_KEY` | ASCII-armored private PGP signing key |
| `MAVEN_SIGNING_PASSWORD` | Key passphrase; omit only for an intentionally unencrypted key |

Create a signing key locally with `gpg --full-generate-key` (RSA signing capability,
4096 bits is a conventional choice), then inspect its fingerprint with
`gpg --list-secret-keys --keyid-format LONG`. Export the private key to a private
local file rather than putting it in repository files or chat:

```sh
gpg --armor --output canopy-signing-private.asc --export-secret-keys YOUR_FINGERPRINT
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys YOUR_FINGERPRINT
```

The second command publishes only the public key so Central can verify signatures.
Store the private key in the signing secret; retain your own secure backup and
remove any temporary export file once setup is complete. Keep the publishing
credentials and private key out of commits, artifacts and logs.

## Prepare and review

The workflow is **Prepare Central prerelease**. Its version must match the
committed `canopyVersion`. Leave `upload_for_validation` false initially. It runs
the required checks, generates real Dokka API documentation, stages and signs all
publications, validates the Maven layout/metadata and builds a ZIP artifact.
Signing is mandatory for this workflow; ordinary local development publication
continues to work without production keys.

Equivalent local commands, with signing environment variables configured:

```sh
./gradlew test ktlintCheck build coverageReport
./gradlew cleanCentralStaging
./gradlew stageCentralPublication -PrequireSigning=true
python3 tooling/central/bundle.py --version 0.1.0-alpha.1 --require-signatures --output build/central-bundle.zip
```

Run the clean command before staging; do not run separate publication builds
concurrently against the shared staging directory. Inspect the bundle and test a
fresh external consumer using the staged matching artifacts. The proposed
version is not available remotely until a deployment is approved and published.
Do not reuse a version after it has been released; change the version and validate
a new bundle instead.

## Optional upload and final publication

After reviewing the bundle, dispatch with `upload_for_validation` true to upload
through the Portal publisher API. The uploader uses `USER_MANAGED`, never
`AUTOMATIC`, and records the deployment ID. It does not call the publish/promote
endpoint. Inspect the deployment in Central and wait for successful validation.
A timeout may still have created a deployment: inspect the Portal before retrying.

After explicit release approval, publish that validated deployment manually in
Central. Then validate the starter in an isolated consumer without `mavenLocal()`
or composite builds, using Maven Central for libraries and the plugin marker.
Only that final download/build/run establishes remote release readiness. Keep
issue #213 open until distribution and this consumer check are complete.

The upload helper's network path is not exercised with real publishing credentials
in local preparation. Local test keys validate signing only; never use them for
an actual release. Windows consumer execution is a separate platform check.
