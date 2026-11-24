# Axycord

Axycord is a native Android Discord client maintained by **Maarij**. It keeps the
classic Android interface and adds support for newer messaging features,
custom themes and APK plugins.

![Axycord logo](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

| Setting | Value |
| --- | --- |
| Client | Axycord |
| Maintainer | Maarij |
| Version | 1.0 |
| Application ID | `mmaarij.axycord` |
| Android | 6.0 / API 23 or newer |
| Updates | Published stable releases in `Axycord/Axycord` |

Axycord is under development. Some features and device behavior still need
verification. See [PLAN.md](PLAN.md) for the current status.

## Repositories and support

- [Axycord](https://github.com/Axycord/Axycord)
- [Axycord Plugins](https://github.com/Axycord/Axycord-Plugins)
- [Axycord Plugin Template](https://github.com/Axycord/Axycord-Plugin-Template)
- [Report an issue](https://github.com/Axycord/Axycord/issues)

## Download and updates

Download a published APK from [Releases](https://github.com/Axycord/Axycord/releases).
Most phones use `app-arm64-v8a-release.apk`. The universal
`app-universal-release.apk` supports every bundled architecture. Releases can also
include `armeabi-v7a`, `x86_64` and `x86` builds.

The app checks GitHub's latest stable release when it enters the foreground,
at most once every six hours per installed source build. The Updates screen also
offers manual checks, release notes, commit history, downloads and installation.
Users start downloads and approve installation. The updater verifies the APK's
size, SHA-256, package, version and signing identity.

## Features

- Servers, channels, DMs, group DMs, threads and forums.
- Messages, replies, reactions, attachments, search, pins and forwarding.
- Emoji, stickers, GIFs, embeds, galleries and video.
- Profiles, presence, display names and profile cosmetics.
- Voice, video, screen sharing and embedded Activities.
- Polls, bot components and application commands.
- Custom themes, local settings and installed APK plugins.

## Build

The app uses Kotlin, Android Views/XML and Gradle. Requirements are JDK 21,
Android SDK Platform 37, Build Tools 36.0.0 and Bash. Set `ANDROID_HOME` or
`sdk.dir` in your local `local.properties`.

This source checkout omits recovered resources, native libraries and the signing
key. Those inputs must be restored before a complete APK can be built. Optional
`app/google-services.json` enables push notifications and must match the app ID.

Run builds through the repository's coordinating script.

```bash
bash scripts/build.sh :app:assembleDebug
bash scripts/build.sh :app:testDebugUnitTest
bash scripts/build.sh :app:lintDebug :plugin-api:lintDebug
bash scripts/build.sh :app:assembleRelease :app:generateReleaseUpdateManifest
```

The app expects `app/axycord-dev.keystore` for local development signing. Keep the
same signing identity for future updates. Build identity comes from Git, and
version codes are derived from the source commit timestamp.

The manual Axycord Build workflow builds this repository's checkout and saves its
APKs and manifest as an Actions artifact. Restore the omitted resources and native
libraries first, then set `AXYCORD_KEYSTORE_BASE64` to the Base64-encoded keystore
with the development alias/password expected by `app/build.gradle.kts`.

| Output | Path |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Universal release APK | `app/build/outputs/apk/release/app-universal-release.apk` |
| Update manifest | `app/build/outputs/update/release/update-manifest.json` |

## Publish a release

Publish a stable release in `Axycord/Axycord` with a tag such as `v1.0`. Attach
the universal APK and `update-manifest.json` from the same build. The updater also
accepts `app-release.apk` as the universal filename.

Attach `app-<abi>-release.apk` files for smaller downloads. Optional baseline
profiles use `app-release-api28.dm` / `app-release-api31.dm` for the universal APK
and `app-<abi>-release-api28.dm` / `app-<abi>-release-api31.dm` for splits.

The release description supplies the update notes. Future builds need the same
application ID and signing certificate, a newer source timestamp and a
nondecreasing version code. Drafts and prereleases are excluded.

## Plugins and project layout

The Axycord plugin API uses the `mmaarij.axycord.plugins.api` namespace.
The packaging plugin ID is `mmaarij.axycord.plugin`, with the `axycordPlugin` DSL.
Plugins built against the previous SDK need to be rebuilt against this API.

| Directory | Purpose |
| --- | --- |
| `app/` | Application, stores, screens, resources and tests |
| `plugin-api/` | Plugin contracts and host services |
| `plugin-gradle/` | Plugin APK packaging and manifest generation |
| `cache-codegen/` | Generated cache codecs |
| `baselineprofile/` | Startup profile generation |
| `third_party/` | Bundled components, provenance and licenses |
| `tools/update-worker/` | Legacy update-service tooling |
| `scripts/` | Build and development tooling |

Read [plugin-api.md](docs/plugin-api.md), [theming.md](docs/theming.md) and
[AGENTS.md](AGENTS.md) before changing their corresponding contracts.

## Attribution and license

Axycord is derived from [Microcord](https://github.com/microcord/microcord), based
on Discord Android 126.21 Stable. Upstream authors retain ownership of their work.
The source license is **AGPL-3.0-only**. See [LICENSE](LICENSE).

Recovered Discord assets and bundled components retain their respective terms.
Native runtime provenance is documented in
[third_party/webrtc-discord-333.5](third_party/webrtc-discord-333.5/README.md).
Vendored protocol definitions include their provenance in `app/src/main/proto/`.

Acknowledgements include [Discord Userdoccers](https://github.com/discord-userdoccers/discord-userdoccers),
[Aliucord](https://github.com/Aliucord/Aliucord/),
[zt64/aliucord-plugins](https://github.com/zt64/aliucord-plugins) and
[BetterChatBox](https://github.com/wingio/plugins/tree/main/BetterChatbox).
Axycord is independently maintained and is not affiliated with Discord Inc.
