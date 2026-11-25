# Built-in Orbs Shop and Quests

These features were ported from Maarij's MIT-licensed plugins:

| Feature | Source | Imported revision |
| --- | --- | --- |
| Orbs Shop | [microcord-orbs-shop](https://github.com/mmaarij123/microcord-orbs-shop) | `df0f82d` |
| Quests | [microcord-quests](https://github.com/mmaarij123/microcord-quests) | `861212f` |

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

The combined client remains licensed under AGPL-3.0-only. The imported portions
also retain their MIT notices.
