# Axycord

Axycord is a native Android Discord client maintained by **Maarij**. It keeps the
classic Android interface and adds support for newer messaging features,
custom themes and APK plugins.

![Axycord logo](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

| Setting | Value |
| --- | --- |
| Client | Axycord |
| Maintainer | Maarij |
| Version | [2.0](https://github.com/Axycord/Axycord/releases/tag/v2.0) |
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

The latest stable release is [Axycord 2.0](https://github.com/Axycord/Axycord/releases/tag/v2.0).
Choose an APK for your device, or use the universal build if you are unsure.

| Device | Download |
| --- | --- |
| Any supported architecture | [Universal APK](https://github.com/Axycord/Axycord/releases/download/v2.0/app-universal-release.apk) |
| Most current Android phones | [ARM64 APK](https://github.com/Axycord/Axycord/releases/download/v2.0/app-arm64-v8a-release.apk) |
| 32-bit ARM devices | [ARMv7 APK](https://github.com/Axycord/Axycord/releases/download/v2.0/app-armeabi-v7a-release.apk) |
| x86-64 devices and emulators | [x86-64 APK](https://github.com/Axycord/Axycord/releases/download/v2.0/app-x86_64-release.apk) |
| 32-bit x86 devices and emulators | [x86 APK](https://github.com/Axycord/Axycord/releases/download/v2.0/app-x86-release.apk) |

Install over your existing Axycord installation, then restart the app. Version 2.0
keeps the application ID and signing certificate used by v1.0 and v1.0.1, allowing
in-place updates that retain app data. Release checksums are available in
[SHA256SUMS.txt](https://github.com/Axycord/Axycord/releases/download/v2.0/SHA256SUMS.txt).

The app checks GitHub's latest stable release when it enters the foreground,
at most once every six hours per installed source build. The Updates screen also
offers manual checks, release notes, commit history, downloads and installation.
Users start downloads and approve installation. The updater verifies the APK's
size, SHA-256, package, version and signing identity. You can also browse
[all releases](https://github.com/Axycord/Axycord/releases).

## What's new in 2.0

- **Voice controls:** repairs per-user volume and local mute, adds the slider's
  0–200 range, and restores the chosen volume consistently when peers reconnect.
  Screen updates no longer interrupt dragging the volume slider or undo mute.
- **Move To:** opens the native voice/stage channel picker and sends the selected
  move, with permission checks and success/error feedback.
- **Recent Mentions:** adds **Mentions you**, **Everyone** and **Roles** filters
  beside Bookmarks. Filter choices survive screen recreation, filtered lists keep
  loading across pages, and holding the bottom Mentions tab no longer opens Search.

Automated checks and signed release-artifact verification pass. Live-device
voice volume/mute/move, reconnect and UI verification remain open; see
[PLAN.md](PLAN.md#release-20) for the verification scope.

## Features

- Servers, channels, DMs, group DMs, threads and forums.
- Messages, replies, reactions, attachments, search, pins and forwarding.
- Emoji, stickers, GIFs, embeds, galleries and video.
- Profiles, presence, display names and profile cosmetics.
- Voice, video, screen sharing and embedded Activities.
- Polls, bot components and application commands.
- Custom themes, local settings and installed APK plugins.
- Built-in Orbs Shop and Quests, each controlled separately in Feature Flags.

Enable **Orbs Shop** or **Quests** under Settings → Feature Flags, then open them
in **Billing Settings**, below User Settings. Disabled features disappear from
that section. Server Boosts remains marked **Coming soon**. Both features start disabled and work
independently of APK plugin loading. Orbs Shop previews collectibles and asks
before spending Orbs. Quests supports browsing, enrollment, completed reward
claims and actual mobile video playback; platform-specific tasks remain tracked
by Discord on their supported platforms. These features require a Discord account.
Their controls use the client theme. Shop thumbnails remain static; clicking a
decoration, nameplate or profile effect opens its animated preview with Pause and
Replay controls. Avatar/nameplate previews show their own surface, while profile
effects use a full-width, vertically scrollable profile card.

Two more built-in features are available in **Settings → Feature Flags**:

- **Components V2 Fix:** fits short cards to their content, plays visible GIF/WebP
  media when GIF autoplay is enabled, and repairs attachment-backed media and
  incomplete Sections in chat, search and forwarded messages. Restart after
  toggling this flag so existing rendered message models are refreshed.
- **Active Now:** shows a heading-free friend carousel below the DM header and a live voice card on
  user profiles. Cards open profiles; permitted voice channels can be joined
  through the client's existing permission, verification and age gates.

Both flags start disabled and are included in settings backup. No plugin APK is
needed. Imported sources retain their MIT notices under
[`third_party/axycord-features`](third_party/axycord-features/README.md).

## Build

The app uses Kotlin, Android Views/XML and Gradle. Requirements are JDK 21,
Android SDK Platform 37, Build Tools 36.0.0 and Bash. Set `ANDROID_HOME` or
`sdk.dir` in your local `local.properties`.

This source checkout omits recovered resources, native libraries and the signing
key. Those inputs must be restored before a complete APK can be built. The required
`app/google-services.json` contains the approved public FCM client configuration
and selects `mmaarij.axycord`. Missing configuration or a mismatched package fails
the build. See [Firebase configuration provenance and delivery checks](third_party/firebase-client/README.md).
Restore missing inputs without replacing the tracked Axycord resources.

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

Publish a stable release in `Axycord/Axycord` with a tag such as `v2.0`. Attach
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
Axycord 2.0 uses plugin API level **3**. Plugins built against the old Microcord
SDK need to be rebuilt against the Axycord API; use the
[Axycord Plugin Template](https://github.com/Axycord/Axycord-Plugin-Template)
and [plugin API documentation](docs/plugin-api.md) when creating a plugin.

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
