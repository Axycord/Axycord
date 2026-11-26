# Firebase client configuration

`app/google-services.json` restores the public Discord-compatible FCM client
configuration supplied in the maintainer's local Microcord checkout at revision
`74f63d6d1dc4a7938306c5a7c6005394c0534204`. Reuse of the Discord client-side
configuration is approved in `PLAN.md`. It contains client identifiers and a client
API key, not Firebase Admin credentials, a service account or a private key.

Only `client_info.android_client_info.package_name` changes from
`dev.mantikafasi.manticord` to `mmaarij.axycord`. The Firebase app ID, project and
sender identifiers remain unchanged because Discord's push sender must match the
token's Firebase project. Changing this local package selector does not register
a new Android app in the provider's project or remove provider-side restrictions.

The Google Services plugin is required for normal app builds. Missing configuration
or a missing matching package fails resource generation instead of silently
producing an APK without push support. Benchmark-only variants retain their
separate package and omit Firebase resources.

Configuration/resource checks and JVM tests do not establish push delivery. Before
publishing a notification hotfix, verify token acquisition and device registration
on a Google Play services device, then real DM and mention delivery while the app
is backgrounded, the screen is locked, and the main process is no longer running.
Check token refresh and notification tap/reply behavior as well. Do not use Android
Force stop as a substitute for ordinary process death, and never uninstall or clear
an existing user's data to run these checks.
