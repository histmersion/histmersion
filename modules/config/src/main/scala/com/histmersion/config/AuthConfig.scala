package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** API key authentication. When `apiKey` is non-empty the server gates every write method (POST/PUT/PATCH/DELETE)
  * behind `Authorization: Bearer <key>`. GET/HEAD/OPTIONS (the web UI, all read APIs) stay open. Empty = no auth
  * (local-only mode).
  */
final case class AuthConfig(apiKey: AuthConfig.ApiKey) derives ConfigReader

object AuthConfig:
  type ApiKey = ApiKey.T
  object ApiKey extends RefinedType[String, Pure]
