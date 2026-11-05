package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** API key authentication. When `apiKey` is non-empty the server gates every write method (POST/PUT/PATCH/DELETE)
  * behind `Authorization: Bearer <key>`. GET/HEAD/OPTIONS (the web UI, all read APIs) stay open. Empty = no auth
  * (local-only mode).
  */
final case class AuthConfig(apiKey: String) derives ConfigReader
