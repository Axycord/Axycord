package com.discord.activities

import java.net.URI

/** Match the HTTPS origin, rather than accepting domains that merely start with its text. */
internal fun isActivityNavigation(url: String, applicationId: Long): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("$applicationId.discordsays.com", ignoreCase = true) &&
        uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)
