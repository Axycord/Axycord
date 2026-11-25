# Built-in features from Maarij's plugins

These features were ported from Maarij's MIT-licensed plugins:

| Feature | Source | Imported revision |
| --- | --- | --- |
| Orbs Shop | [microcord-orbs-shop](https://github.com/mmaarij123/microcord-orbs-shop) | `df0f82d` |
| Quests | [microcord-quests](https://github.com/mmaarij123/microcord-quests) | `861212f` |
| Components V2 Fix | [microcord-v2-component-fix](https://github.com/mmaarij123/microcord-v2-component-fix) | `0b2966e1589e906166cf8c16a2af5e755798612f` |
| Active Now | [microcord-active-now](https://github.com/mmaarij123/microcord-active-now) | `11ca683728fec21c29179178f72f34e7d10e180f` |

The original MIT licenses and third-party notices are preserved in the adjacent
directories. Those notices describe the original plugin distributions and paths.
In Axycord, the sources live in `com.discord.widgets.orbsshop` and
`com.discord.widgets.quests`; the imported icons use the `ic_axycord_` prefix.
Discord artwork and media retain their original rights holders.

Plugin entry points, discovery services and activity lifecycle injectors are
excluded. Native settings flags and restorable app screens own the features.
Requests use Axycord's default REST transport, with session-bound authorization,
cancellation and feature/account checks. Video playback uses the existing
ExoPlayer core/UI modules and the matching 2.19.1 HLS module.
Controls and fonts follow the native client theme. Balances appear in page
content. Clicked previews reuse native avatar decoration, nameplate and profile
effect animation renderers, with playback scoped to the preview lifecycle.
Catalog thumbnails stay static; the plugin's separate APNG renderer is excluded.

Components V2 repairs run on the render projection, including loaded history and
forwarded snapshots, without changing stored messages. Compact widths and media
playback are scoped to native component views; the plugin's activity-wide scan,
receive interceptor and repaired-preview menu are unnecessary in the host.
Active Now uses native DM/profile views and the existing presence, relationship,
permission and voice stores. Profile colors come from the resolved native palette.
Both integrations have independent, backup-aware feature flags and do not register
plugin services, plugin UI contributions or an alternative REST client.

The combined client remains licensed under AGPL-3.0-only. The imported portions
also retain their MIT notices.
