# Axycord Development Plan

This document tracks architecture, compatibility decisions, feature status and
remaining work. [README.md](README.md) covers installation and building;
[AGENTS.md](AGENTS.md) contains the detailed development rules.

Update the relevant section when behavior or evidence changes. Do not append
session transcripts, tool output, repeated build summaries or agent journals.
Implementation history and older audit reports are available in Git history.

## Project Contract

| Area | Decision |
| --- | --- |
| Reference | Discord Android 126.21 Stable; use newer client/runtime evidence for intentional backports |
| Goal | Preserve the native Android experience and reconstruct complete feature behavior |
| Language and UI | Readable Kotlin, Android Views and XML, generated view bindings |
| Application ID | `mmaarij.axycord` |
| Namespace | `com.discord`; JNI-facing names remain exact |
| Current build | Minimum SDK 23, target SDK 36, compile SDK 37; see `app/build.gradle.kts` |
| State | Existing store/dispatcher/fragment architecture |
| Resources | Retain recovered assets, layouts and qualifiers |
| Dependencies | Install public libraries normally; versions live in `gradle/libs.versions.toml` |
| Native runtime | Preserve the packaged binaries' JNI contracts and documented WebRTC compatibility |
| Reference files | Read-only; never edit the decompiled reference to make the target build |

The original API 21 goal differs from the current API 23 minimum. API 21 support
must not be claimed without reconciling the build configuration and validating
that compatibility. This documentation cleanup does not change the SDK settings.

## Architecture

### Application and state

- `App` initializes the process; `AppActivity` hosts fragments and dedicated
  entry points provide call, share and Android task behavior.
- `StoreStream` owns the store graph. All `StoreV2` mutations run on its single
  registered dispatcher; `ObservationDeck` publishes post-dispatch snapshots.
- Stores include their complete semantic state: caches, indexes, persistence,
  loading/error state and update sources, not just fields required by one screen.
- Models preserve types, nullability, constructor order, JSON names, enum wire
  values and Kotlin value semantics. Secret-bearing string output is redacted.
- SharedPreferences and Kryo-backed `Persister` retain settings and store data.
  Account-specific state must not leak across logout or account switching.

### Networking and startup

- Discord REST endpoints use `RestAPI.api` and `ClientMetadataInterceptor`.
  Do not restore the former legacy/RN client split.
- The Gateway uses typed dispatch payloads, Gson streaming, `zstd-stream`,
  identify/resume, sequence tracking, heartbeats and reconnect/backoff.
- Fast Connect opens one early socket and buffers events until store listeners
  are ready. `CLIENT_STATE_V2` versions support full/partial READY hydration.
- Cached authenticated UI opens before READY; known required actions still take
  precedence. Network work must not block first-frame rendering.
- Captcha and MFA challenges use the shared transport/coordinator paths rather
  than separate implementations in every screen. Cancellation remains distinct
  from a transport failure.
- The public updater and Activity bearer-token authentication use separate
  transports where Discord account credentials must not be attached.

### UI and media

- Use existing Discord-themed components, generated bindings and lifecycle-aware
  subscriptions. Dynamic profile children consume the resolved profile palette.
- Preserve toolbar Up/Back behavior, touch targets, RTL, large fonts and recycled
  view state. Decorations should not alter existing geometry without a concrete
  requirement and recorded evidence.
- Images use the shared Vito/Fresco pipeline. APNG stickers use `ApngDrawable`,
  GIFs use the image pipeline, and Lottie stickers use the native renderer.
- Voice and streams share the owning media engine. Voice uses the `default`
  native context; streams use separate stream contexts to avoid replacing the
  voice/camera sender. Preserve exact JNI descriptors and callback lifetimes.
- Several Go Live streams can be watched at once, as on desktop and React Native
  (a deliberate divergence from 126.21's single stream). Stream state and RTC
  connections are keyed by stream key; single-stream callers read a primary
  stream (our broadcast, else the latest watch). Simultaneous playback of more
  than one stream has not been observed on a device yet.
- Native provenance and verification are documented under
  [third_party/webrtc-discord-333.5](third_party/webrtc-discord-333.5/README.md).
  The obsolete 126.21 bridge and extractor have been removed; their provenance
  remains in Git history.

### Plugins

Bundled and installed-APK plugins use the typed host API, deterministic dependency
and priority handling, lifecycle-scoped contributions and host-rendered settings.
Asynchronous plugins do not gate first UI; explicitly blocking dependency chains
have bounded startup waits. The master loading switch gates discovery and loading.
External plugins execute in-process and are not sandboxed.

Browse Plugins reads only Axycord's `Axycord-Plugins` catalog. Message Logger,
ReviewDB, Translate and Last.fm now have separately maintained Axycord ports under
`mmaarij123`, replacing the previously removed inherited listings by explicit
maintainer request. Each has source, documentation, preserved licensing and a
signed 1.0.0 release. Their package/action/SDK identifiers use `mmaarij.axycord`.
An unpublished catalog (HTTP 404) displays the existing empty-list state.

Catalog entries can name a `variant` while retaining the same plugin ID, package,
version and signer. The browser distinguishes installed variants by APK size and
offers a same-version replacement for the other variant; it never offers a version
downgrade. Downloads allow up to 200 MiB and stream through a bounded buffer while
checking size and SHA-256. Partial or mismatched files are discarded. This supports
both Full and Compressed Emoji Replace listings. Release 1.0.1 includes this
support; v1.0 retains its old 50 MiB cap and requires a client update to show Full.
The catalog contains eight listings across seven source modules: the four new
ports, APlatformIndicators, Emoji Replace (Compressed and Full), and Permissions
Viewer. Existing release bytes/signers are preserved. Catalog CI verifies all
eight APKs and builds/lints/tests the source modules before publishing; the live
CDN index is verified. The four new ports fit published v1.0's size limit.
Six focused client JVM tests and app compatibility lint pass for variant selection
and bounded downloads. This source ships in 1.0.1; on-device variant installation
verification remains open.

Message Logger history and all ReviewDB text descendants explicitly resolve host
Discord normal/bold/semibold fonts, falling back to bundled Whitney faces rather
than system fonts. This includes dynamic rows and nested editors. ReviewDB vote
feedback is inline; Translate and Last.fm reuse native host text/settings/dialog
and presence views. Eight port regression tests and all four standalone source
CIs pass. History snapshots, translation subscriptions, ReviewDB executor shutdown
and Last.fm polling generations/text padding are hardened in the ports. Device
rendering, live account operations and third-party service behavior remain unverified.

See [docs/plugin-api.md](docs/plugin-api.md) for the current API, signer approval,
resource loading and contribution surfaces.

### Built-in Orbs Shop and Quests

Maarij's MIT-licensed Orbs Shop and Quests sources are integrated as native
features, independently of plugin discovery/loading. Separate registered Boolean
flags start disabled and participate in client settings backup. Enabled features
are accessible from Feature Flags and Billing Settings below User Settings.
Disabled feature rows are hidden immediately. Server Boosts remains an explicit
Coming soon destination. The settings toolbar contains Search only; Switch Account
sits below My Account, Debug ends Accessibility, and red Logout ends App Information.
Quest/Shop use native button styles, theme fonts and content balance labels.
Catalog thumbnails remain static. Clicking an avatar decoration, nameplate or
profile effect activates the native APNG renderer, with Pause/Replay controls;
previews do not equip items or depend on the separate equipped-cosmetic display
flags. This is an explicit animation opt-in confined to the shop preview, including
when reduced motion is enabled elsewhere. Focus loss, detach, variant changes and
closing the preview stop/cancel playback. Avatar and nameplate previews show only
their own surfaces; effects/frames use a full-width profile whose natural height
scrolls rather than shrinking the card to fit. Native effect layers now preserve
full-canvas bitmap proportions, positioned layer bounds and loop delays. The
plugin's separate animation renderer remains excluded.

Restorable native screens and session-bound calls gate both features on their
flags, Discord backend and original account credentials. Closing a screen,
disabling its flag or switching accounts cancels its pending feature calls;
detaching video releases playback. All REST endpoints extend `RestAPIInterface`
and use the default client transport. ExoPlayer's matching 2.19.1 HLS module is
added for quest videos. Catalog caches omit balances/purchases/credentials, and
redemption rechecks the current price, collection and balance after confirmation.
Quest progress records observed playback; desktop/console tasks remain tracked
on their supported platforms. Import revisions and licenses live in
[third_party/axycord-features](third_party/axycord-features/README.md).

Verification: 40 focused app JVM tests passed for settings section/menu order, Logout
icon/text tint, invite onboarding gates, catalog/prices, cache round trips,
effect layout, theme contrast, quest parsing/actions, saved video validation,
playback accounting, session gates and all 12 inherited endpoint request shapes.
Default-client metadata, flag registry, onboarding-store and invite-race tests
also passed. Full debug Kotlin/Java compilation and compatibility lint for both
the app and Plugin SDK passed. Missing recovered resources/assets/native libraries
were restored locally from the maintainer's November 24 checkout for the authorized
private debug build. Public source still omits those recovered inputs. The selected
shop-preview regression set passes 22 JVM tests covering catalog defaults, variant
assets, timing metadata, full-width effect geometry and nameplate rows. App
compatibility lint passed. The new native effect-loop/cleanup instrumentation
fixture compiles separately against the app classes; it has not run on a device.
The previous instrumentation compile reported outdated constructor/API calls in
EmbeddedActivityWebViewSmokeTest and NativeEngineLifecycleTest. Those fixtures now
match the current source signatures; their recompilation remains unverified.
No device or live redemption/quest verification was performed; runtime checks
remain open, including selected-preview animation and rendering on test hardware.

### Built-in Components V2 Fix and Active Now

Maarij's MIT-licensed plugins are integrated behind separate Boolean flags, both
disabled by default and included in client settings backup. Source revisions and
original notices are recorded under `third_party/axycord-features/`.

Components V2 repairs project attachment metadata and incomplete Sections before
native component mapping for chat, search and forwarded snapshots. Stored messages
remain unchanged; wrapper and child order preserve interaction indices. Native
container views fit supported short text/single-media cards to content; other cards
retain their existing width. Visible thumbnail/gallery GIFs use the existing image
pipeline and GIF-autoplay preference. Spoilers, focus loss, hidden windows and detach
stop owned playback. Widths restore when the flag is off or a card is detached.
Changing this flag requires the offered app restart to refresh cached render models.

Active Now is a heading-free, swipeable carousel below the native DM header. Presence,
friend/ignored lists and permission-filtered voice states drive its carousel;
selecting a card opens the native user sheet. Non-bot profiles have a palette-aware
voice card with permission-rechecked joining through the existing microphone,
verification, spoiler, age, stage and capacity gates. View detachment disposes
subscriptions; turning the flag off disconnects voice/feed observation and hides
the feature. No plugin discovery, activity injector or extra network client is used.

The DM header uses the former bottom-tab search vector as one 48dp search action,
opening global search. The full search field and group-invite button are removed.
Bottom navigation and its customization list omit Search, including restored
visibility masks and old saved layout entries. Navigation enum ordinals remain
stable. The sidebar uses generated view binding and guards deferred scrolling
against view destruction. App compilation, resource linking and compatibility lint
pass. AAPT requires the named `noScroll` flag instead of literal `0` on the channel
header. On-device layout verification remains open.

Source checks pass: Kotlin syntax parsing for 23 changed/new files, XML parsing,
new string and binding references, flag registration/UI wiring, sidebar placement,
source-port comparison and original license/notice preservation. This is not type
checking or runtime verification. The maintainer subsequently authorized the 1.0
release build; current compiler and JVM evidence is recorded below.
Ported regression fixtures cover repair identity, attachment/snapshot boundaries,
interaction slots, compact width, owned animation cleanup, friend selection and
voice visibility/permissions. Their JVM suites pass, as do app compilation and
compatibility lint. Dedicated-device UI/voice checks remain open.

### Invite onboarding

Invite acceptance captures membership before the POST so subsequent guild
hydration cannot turn a fresh join into an apparent existing membership. Repeat
invites honor member completion flags and local completed state; established
members with missing member-cache data are not forced through onboarding again.
An explicitly started, unfinished flow can resume. Fresh joins, including rejoin
after leaving with stale local completion, still receive required onboarding.
Join-request verification retains priority over onboarding.

## Feature Status

These are delivery statuses, not percentage-complete estimates. Structural
coverage and passing builds do not establish complete behavioral parity.

| Phase | Area | Status | Remaining verification/work |
| --- | --- | --- | --- |
| 0 | Build and XML shell | DONE | Maintain build and launch regressions |
| 1 | Resources, themes and custom views | IN PROGRESS | Qualifiers, accessibility, animations and full view behavior |
| 2 | Original native media spike | DONE | Historical x86_64 lifecycle evidence; not a blanket voice-parity claim |
| 3 | Core runtime and stores | IN PROGRESS | Reconcile remaining graph/init/reset seams and full semantic state |
| 4 | REST and Gateway | IN PROGRESS | Destination-store behavior, malformed payloads and live compatibility |
| 5 | Authentication and navigation | IN PROGRESS | Provider flows, required actions and full route/device matrix |
| 6 | Guilds and channels | IN PROGRESS | Reorder, permissions, lazy members, persistence and large-guild behavior |
| 7 | Messages and uploads | IN PROGRESS | Offline/reconnect reconciliation, retry, uploads and media regressions |
| 8 | Users and social features | IN PROGRESS | Profile/presence consistency and provider/device behavior |
| 9 | Notifications and background work | IN PROGRESS | Cold-process delivery, suppression and notification-action matrix |
| 10 | Settings and accounts | IN PROGRESS | Nullable updates, account/session flows and persistence regressions |
| 11 | Voice, calls and video | IN PROGRESS | Join/move/reconnect, audio routes, backgrounding, overlays and teardown |
| 12 | Extended features | IN PROGRESS | Activities, moderation, integrations and remaining behavioral parity |
| 13 | Billing and external integrations | IN PROGRESS | Project-owned products, OAuth/App Links and provider configuration |
| 14 | Release hardening | IN PROGRESS | Device/layout matrix, shrinking/keep rules and full regression boundary |
| 15 | Generated view bindings | IN PROGRESS | Remaining static view lookups and justified dynamic-lookup exceptions |

### Recorded evidence

- The source audit inventories a baseline of 3,133 tracked Kotlin files and parses all 475
  tracked XML files without XML errors. This is a static inventory, not a claim
  that every path has been exercised. The Kotlin parser has known grammar gaps;
  changed code is checked against the existing baseline rather than treated as
  compiler-verified by that parser. All 38 update-worker Node tests pass.
- Audit fixes include exact HTTPS-origin matching for embedded Activity navigation,
  request-generation-safe APNG cancellation and single-owner nameplate disposal,
  exact quest playback checkpoints with recovery after rejected jumps, and native
  display-name fallback for blank Active Now nicknames. Unreachable legacy OAuth
  serializers, duplicate send-update handling, an unused ack-work factory and HQ
  directory predicate, the obsolete bottom-search callback and plugin-era section
  disposal hook are removed. Persisted worker classes and public protocol/JNI
  contracts remain represented. Compact V2 cards retain one measurement cache per
  owned view instead of a map keyed by that same view. App compilation and
  compatibility lint pass; dedicated-device verification remains open.
- Instrumentation fixtures now use the current embedded-WebView constructor and
  media-engine parameters. The unused full-session WebView smoke fixture is removed;
  the test still checks hardened settings offline. This is a source correction,
  not a successful instrumentation compile or device run.
- The 1.0 release checks compile app Kotlin/Java, link resources, and pass app/SDK
  `NewApi` lint and all four SDK JVM tests. The full app run executes 2,443 tests:
  2,414 pass, 28 fail and one is skipped. Shop, Quest, Active Now, Components V2,
  invite onboarding, HTTPS-origin and updater suites pass. The registry fixture's
  old 34-flag count is updated to 38 for the four built-in features; the focused
  release set then passes all 85 JVM tests. The remaining
  full-suite failures need reconciliation; a successful release build does not
  establish a green full regression boundary. Instrumentation compilation remains
  blocked by stale `ProfileThemeLayoutTest` note bindings and theme signatures;
  no device tests are run. Firebase configuration was absent from the published
  1.0 APK, so its push transport is unavailable. Release builds display `Release 1.0` rather than
  the development label.

- Historical structural checkpoints recorded 1,228 retained layout variants,
  76/76 first-party custom-view classes and 83/83 typed reference Gateway events.
- The recorded REST ledger reached 264 effective historical routes; the modern
  generalized MFA route intentionally replaces the exact legacy TOTP template.
- Store, widget and DTO counts used different audit scopes and dates. No fresh
  whole-project behavioral coverage percentage is established here.

- A previous full verification boundary recorded 1,678 JVM tests and 59 connected
  tests, plus debug/release builds and lint. Those historical results are not a
  claim that the current worktree passes every check.
- Device observations have confirmed concurrent incoming/outgoing voice, camera,
  screen sharing and remote playback. The broader lifecycle/device matrix remains
  open, so Phase 11 remains in progress.
- The September 13 updater changes passed 21 focused JVM tests and 32 worker tests.
  The user confirmed an automatic update banner after an explicitly approved
  in-place downgrade to build 191 and resetting only `next_check`.
- Component link-preview embeds (`type: components`) reuse the Components V2 renderer for
  containers, markdown, separators, sections, media and read-only link buttons; a focused
  deserialization-to-row fixture passed before the in-place device install.
- Profile crops decode GIF and animated WebP through Fresco and encode animated GIF
  with `io.github.waynejo:androidndkgif:1.0.1` from Maven Central. Outputs use the
  existing `image_provider/` FileProvider root and retain `image/gif` upload MIME.
  Android runtime checks preserved all 96 distinct frames and 8,010 ms of the actual
  archived WebP avatar; GIF timing/transparency checks also passed. Crop chrome was
  visually checked, and archived-avatar downloads show the existing loading spinner.
  Debug build/in-place install passed; the user confirmed the corrected flow works.

### Notification hotfix 1.0.1

The approved Discord-compatible client configuration is restored from the local
Microcord checkout, with only its Android package selector changed to
`mmaarij.axycord`. Firebase project/app/sender identifiers remain unchanged;
provenance and the runtime verification checklist are in
`third_party/firebase-client/README.md`. Normal app builds require the Google
Services plugin: release resource-generation checks reject missing configuration
and a mismatched package. All 28 selected notification JVM tests and app/SDK
Android compatibility lint pass.

A narrow Firebase Installations probe using Axycord's actual package and release
certificate was accepted and received an installation authentication token; the
temporary installation was deleted without logging or saving credentials. The
maintainer subsequently confirmed notifications arriving on a friend's phone and
approved public release on November 26, 2026. Exact cold-process, suppression,
token-refresh and notification-action coverage remains unverified; Phase 9 stays
in progress.

[Public release v1.0.1](https://github.com/Axycord/Axycord/releases/tag/v1.0.1)
publishes the tested candidate from `0185f82a3298cf38e9d981d2433d84c6a1225789`,
version code `217852506`, with the same signing identity as v1.0. All five APKs
were verified for Firebase resources, notification components, package/version,
embedded source revision and v1/v2 signatures. The release includes its updater
manifest and checksums and is marked latest stable.

## Compatibility Decisions

| Area | Retained decision |
| --- | --- |
| Axycord branding | Maintained by `mmaarij`. Client text, source/resource names and theme exports use Axycord. Application ID is `mmaarij.axycord`, version name is `1.0.1`, and updates come from `Axycord/Axycord` GitHub Releases. Plugin API, Gradle IDs and codegen use `mmaarij.axycord`; plugins require a rebuild against the renamed SDK. Cloud backup and theme imports retain legacy format compatibility. Discord namespaces and JNI names preserve their binary contracts. |
| Modern features | Backport confirmed modern usernames, profile cosmetics, polls, forwarding, bot components, Apps/Activities and other explicitly requested features without replacing the native UI architecture |
| Display names | Use the shared guild nickname/global name/username resolution; do not invent precedence separately per screen |
| Gateway | Use zstd only, streaming Gson and CLIENT_STATE_V2; the Fastjson2 experiment and zlib fallback were removed |
| REST identity | One default Discord client-metadata transport; endpoint-specific parallel RN clients are retired |
| Captcha | Shared bounded challenge handling; hCaptcha is supported, unsupported challenge providers fail explicitly |
| Activity OAuth | Explicit user authorization; bearer authentication must not receive the Discord account token or cookies |
| Native binaries | Compiled bridge preservation was approved where public artifacts could not satisfy the exact JNI contract; keep provenance and lifecycle checks |
| Firebase | Reuse of Discord's client-side FCM configuration was explicitly approved on 2026-08-11; Admin/service-account credentials are excluded |
| Giphy | The embedded API key is a public client key, confirmed by the user; retain it |
| External services | Missing project-owned OAuth, products or signing relationships must degrade safely; Samsung OAuth is out of scope |
| Signing | Axycord builds use a stable maintainer-supplied `app/axycord-dev.keystore`. Local development defaults use the `axycord` alias/password. CI restores that identity from `AXYCORD_KEYSTORE_BASE64`; signing files are excluded from Git. |
| Releases | The manual Axycord Build workflow saves APKs and the update manifest as GitHub Actions artifacts. Stable app releases are published in `Axycord/Axycord`. |
| Vendored dependency | `com.github.mmin18:RealtimeBlurView:82df3520b4` (v1.2.1, Apache 2.0) is checked in as an AAR under `third_party/maven` and resolved from there: JitPack stopped serving that version (404), so a machine without a warm Gradle cache could not build. The older `f54dc3c34d` that JitPack still serves lacks v1.2.1's fallback blur implementation, so it was not substituted |
| CI runner | `.github/workflows/build.yml` builds the current Axycord checkout on Ubuntu with JDK 21. Recovered Android resources, native libraries and the signing identity must be supplied before APK builds can complete. The workflow has not been run end to end in this checkout. |
| Version identity | Source SHA and commit timestamp identify builds; versionCode is commit timestamp minus the 2020 epoch, not the CI build number |
| Bookmarks | Synced viewing, jumping and removal are retained; local bookmark creation was withdrawn by user request |
| Nearby friends | Removed by user request; the retired Google Nearby Messages backend has no supported transport |
| Diagnostics | Redact tokens, cookies, passwords, MFA/captcha values and message content, including exception text |
| Resource compatibility | Restore first-party attributes; let dependencies own their attributes. Retain decoded-layout compatibility repairs and the inherited lint baseline |
| Observer errors | Preserve the DEX-proven `ObservationDeck` exception/log-level behavior; do not remove it based on incomplete JADX output |
| Minimum API safety | `NewApi` lint is fatal for `:app` and `:plugin-api` (CI step before the debug build), because one call newer than minSdk 23 crashed Android 6 at launch. Desugaring does not rewrite `CompletableFuture`, so plugin startup uses the `streamsupport-cfuture` backport; `java.time` and streams are desugared and fine |
| Custom themes | Runtime `ResourcesLoader` overlay (API 30+, no hooks): a generated resource table replaces palette colors and a placeholder overlay style that every themed context applies. Format is Aliucord theme JSON plus `attrs`/`axycord`; Aliucord's by-value color swaps are replayed through a table generated from the reference. Icon tints, fonts and sounds are table entries served by the loader's `AssetsProvider`; the background image is baked once and shown per Themer's transparency modes. Android 6-10 keep the built-in themes. Discord's Nitro client themes (23 presets, and custom themes synced from other clients) are generated into the lowest layer; anyone can apply one, and it syncs only for Nitro; see [docs/theming.md](docs/theming.md) |
| Report flow | The modern report menu is parsed leniently: element types newer than 126.21 (free text, dropdowns, radio groups) are skipped, not rendered. The post-report Block button uses the standard fit button size instead of the reference's 32dp, by user request |
| Guild header | RN's guild actions row (search, invite, events) replaces the reference events row, and every community server gets RN's Browse Channels row (Channels & Roles once onboarding has prompts), by user request |
| Startup caches | The channels, guilds and roles Persister caches (the startup-critical, largest ones) bypass Kryo's reflective field serializer: `@CachedFormat` models get a field-by-field codec generated at compile time by the `:cache-codegen` KSP processor, approved by the user on 2026-11-13. Each file's header carries a generated layout hash, so a model change discards the cache and the gateway refills it at READY (the restore drops guild versions whose data was lost). Measured on a 10.5k-channel account: cache restore 175 ms → ~50 ms. kotlinx.serialization was prototyped and rejected, decoding channels about 2x slower than the hand-written equivalent |
| ConstraintLayout helpers | `DiscordViewInflater` swaps every inflated `<Barrier>`/`<Group>` for a subclass that resolves `constraint_referenced_ids` once per process; ConstraintLayout's own name lookup (reflection, then `getIdentifier`, repeated on every attach) cost ~30 ms of the first chat frame. Recovered layouts are unchanged |
| Network routing | By user request (2026-11-13): a proxy (`ClientProxy`, an app-wide `ProxySelector`; voice UDP, WebViews and DownloadManager are not covered) and alternative backends (`ClientBackend`: API, CDN, media, Gateway and web hosts, Spacebar discovery). The backend is fixed per process and can only be changed while signed out; each stored account records its backend, and switching to an account on another backend restarts onto it so a token never reaches another server. Endpoints must be https/wss |
| Tablet mode | By user request (2026-11-23): windows at least 720dp wide (tablets, WSA/desktop windows) dock the guild and channel list beside the chat like the desktop client, with a user panel (`HomeUserPanel`) at its foot in place of the bottom tab bar; Friends, Mentions and Settings open beside the list. The member list docks on the right, toggled by the members button and shown by default from 1100dp. Pushed screens, settings and mentions cap at 840dp and bottom sheets at 600dp, centred; chat media caps at 550dp and embed, file and app cards at 432dp, as on desktop. `OverlappingPanelsLayout.isDocked` gates it; narrower windows keep the reference's sliding panels. Checked on WSA |
| Mention search | `@name` autocomplete shows a loading row while its gateway member search is out, by user request; the reference shows nothing. The request's nonce is its guild id (126.21 and React Native send none), so only an autocomplete `GUILD_MEMBERS_CHUNK` clears the row; a 10 s timeout covers a lost reply |

Exact dependency versions belong in the version catalog, not in historical
installation logs. Reference-specific behavioral deviations remain documented
beside the implementation; investigate their evidence before changing them.

## Updater and Distribution

- Stable releases are published at https://github.com/Axycord/Axycord/releases.
- The app queries GitHub's public latest-release API, then reads the release's
  `update-manifest.json` and universal APK. Drafts and prereleases are excluded.
  No published release is treated as no available update.
- Each release must attach the same build's `app-universal-release.apk` (or
  `app-release.apk`) and generated manifest. The manifest binds source identity,
  package/version, universal APK size and SHA-256. GitHub asset digests cover
  optional per-architecture APKs and baseline profiles.
- Downloads are restricted to the configured repository and release. Downloaded
  bytes, APK package `mmaarij.axycord`, version and signing identity are verified
  before installation. Version name is currently `1.0.1`.
- Release descriptions provide notes. GitHub's compare API supplies commit history
  when available, with release notes retained if comparison is unavailable.
- Automatic checks run on foreground entry, at most once every six hours for the
  same source build. Checks and update notices reuse the existing settings and UI.
- Downloads and installation start through the user's actions. DownloadManager
  owns background downloads. Optional verified baseline profiles install beside
  their APK through PackageInstaller, falling back to the APK-only installer.
- Older persisted worker downloads remain readable. The worker under
  `tools/update-worker/` is optional legacy tooling configured for Axycord's
  GitHub repository. Its Cloudflare account and custom domain are maintainer-supplied.

Publishing requirements are documented in README.md under Publishing app updates.

## Open Work and Risks

1. **Runtime parity:** verify remaining store dependencies, screen actions and
   lifecycle behavior against the reference and controlled runtime observations.
   Do not carry obsolete missing-class lists forward as current facts.
2. **Messaging reliability:** exercise offline sends, reconnects, upload retries,
   optimistic replies, pagination and recycled media under poor connectivity.
3. **Voice and notifications:** complete background/cold-process, device-switch,
   reconnect and teardown checks on dedicated test hardware.
4. **Integrations:** project-owned OAuth/App Links and billing products remain
   separate from reconstructing the UI and transport. New Spotify account linking
   and unsupported captcha providers require their own confirmed integration work.
5. **UI coverage:** finish static view-binding migration and validate RTL, large
   fonts, tablet/landscape layouts, accessibility and dynamic profile palettes.
   Custom themes are checked on Android 16 only; icon tints, wallpapers, fonts
   and sounds still need a device pass, and Android 11-13 is unverified.
6. **Release checks:** reconcile the full-suite failures in Gateway, store fixtures,
   premium slots, missing compiled-theme XML fixtures, application directory/launcher,
   channel-list MFA separator, expression/voice payload contracts and Home UI delivery.
   Update `ProfileThemeLayoutTest` for current note bindings and theme signatures.
   The full regression boundary remains open.
7. **Minimum Android version:** resolve the historical API 21 contract against the
   current API 23 build; do not silently claim support for devices the APK excludes.
8. **Backend drift:** use sanitized runtime traffic to resolve uncertain HTTP and
   Gateway behavior. Source presence alone is not proof that a route still works.

## Reference Evidence

| Source | Use |
| --- | --- |
| Controlled runtime observations | Actual client, backend and device behavior |
| DEX/smali, `javap`, Vineflower output | Exact signatures, instructions, declaration order and missing JADX bodies |
| Manifest, resources and payloads | UI structure, platform contracts and serialization |
| Kotlin metadata and callers | Nullability, names and corroboration |
| Decompiled JADX Java | Navigation aid; verify incomplete or suspicious bodies against stronger evidence |

The local reference locations and detailed interpretation rules are in
[AGENTS.md](AGENTS.md). A broken decompile is an unresolved evidence gap, not
permission to invent behavior. Enum ordinals come from bytecode, and apparent
constructor defaults must be checked against synthetic constructor masks.

## Reconstruction Campaign

Work in complete vertical slices: dependencies and state first, then UI and
verification. When contributors work concurrently:

- Assign exclusive file ownership and agree on cross-package API names.
- Use `scripts/build.sh` for every Gradle invocation and wait for its lock.
- Coordinate Git operations; never stage or overwrite another contributor's work.
- Defer cross-package cleanup to its owner and leave files self-consistent.

### Definition of done

A slice needs reference/runtime evidence, complete state and UI behavior, a
meaningful regression check and the relevant successful build checks. Loading,
empty, error, lifecycle and accessibility behavior are part of the feature.
List anything unverified explicitly; placeholders do not count as parity.

Handoffs should name the changed paths, behavior, checks and unresolved issues.
For remaining work, give the target path, reference source and next action.
Keep durable conclusions in the relevant section of this plan, not a session log.

## Verification

Run from the repository root; use Git Bash on Windows:

```bash
bash scripts/build.sh :app:assembleDebug
bash scripts/build.sh :app:testDebugUnitTest --tests 'com.discord.utilities.updates.AppUpdateTest'
bash scripts/build.sh :app:lintDebug
bash scripts/build.sh :app:assembleRelease :app:generateReleaseUpdateManifest
node --test tools/update-worker/worker.test.mjs
```

Use `--offline` only when the required dependencies are cached. Broader tests
and release checks are required before closing their corresponding phases, not
after every small edit.

Preserve the user's installed app and login. Never uninstall, clear app data or
run connected/Orchestrator tests on the daily-use A065 (`188b1209`). Use a
dedicated emulator/device for instrumentation. Device updates are in-place;
an explicitly approved downgrade must also retain data. Stop on signing/version
conflicts rather than falling back to uninstalling.
