# Axycord release worker

Maintained with Axycord by mmaarij. The Android app reads GitHub Releases directly.
This optional worker exposes the older `latest.json` feed and pinned download
routes for installations that need that interface.

## Source and deployment

Release metadata comes from `https://api.github.com/repos/Axycord/Axycord`.
Binary redirects are restricted to `https://release-assets.githubusercontent.com`.
The app package is `mmaarij.axycord`.

The worker's deployment name is `axycord-updates`. No Cloudflare account ID or
custom domain is committed. Select your own account when deploying, then supply
your own domain if a custom address is needed.

Set the `GITHUB_TOKEN` Worker secret to a token with read access to the release
repository. Credentials are used only for GitHub API requests. They are excluded
from public responses and redirected downloads.

## Release assets

This legacy feed uses published stable tags in the form `build-N`.
Attach `app-release.apk` and `update-manifest.json` from the same build.
Optional split APKs use `app-<abi>-release.apk`, where ABI is `arm64-v8a`,
`armeabi-v7a`, `x86_64` or `x86`.

The manifest contains `commitSha`, `commitTimestamp`, `versionCode`,
`versionName`, `packageName`, `sha256` and `size`.
Its package must be `mmaarij.axycord`, and its size and digest must match the
universal APK. Baseline profiles use `app-release-api28.dm` and
`app-release-api31.dm`, with the ABI inserted before `-release` for splits.

Axycord's direct GitHub updater also accepts ordinary tags such as `v1.0` and
the generated universal filename `app-universal-release.apk`.
See the root README for current app release instructions.

## Routes

- `GET /latest.json` returns the latest validated release metadata.
- `GET /latest.apk` and `/latest-<abi>.apk` redirect to pinned release downloads.
- `GET /releases/build-N.apk` and `/releases/build-N-<abi>.apk` serve APK downloads.
- `GET /releases/build-N/changes.json?from=<installed-sha>` returns bounded notes and commit history.
- The corresponding `-api28.dm` and `-api31.dm` routes serve optional profiles.

Latest-release metadata is cached for 60 seconds. Immutable APKs and profiles use
digest-keyed edge caches. Missing or invalid manifests, packages, digests and
redirect destinations fail validation. Downloads carry public headers only.

## Verification

```bash
node --test tools/update-worker/worker.test.mjs
```
