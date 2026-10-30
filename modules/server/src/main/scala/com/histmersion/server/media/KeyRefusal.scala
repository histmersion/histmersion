package com.histmersion.server.media

import com.histmersion.pluginhost.{europeanaExclusions, excludedProviders, latestYear, Polite}

/** What a key-gated API's refusal means, said once for both collections. */
private[media] object KeyRefusal:
  /** Pure: an HTTP 401/403 of a key-gated API named as a refused key, with the setting to check. */
  def explain(e: String, plugin: String): String =
    if e.contains("HTTP 401") || e.contains("HTTP 403") then
      s"$e — the API key was refused (check histmersion.plugins.$plugin.api-key)"
    else e
