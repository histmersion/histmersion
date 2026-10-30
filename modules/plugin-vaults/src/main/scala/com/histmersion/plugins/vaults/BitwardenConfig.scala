package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

import scala.concurrent.duration.*

// ── Bitwarden ───────────────────────────────────────────────────────────────────────────────────

/** `bitwarden:<item id or search>[#<field>]` (`field` defaults to `password`). `bw` reads an unlocked vault through
  * `BW_SESSION`: `session` (a Secret, e.g. `env:BW_SESSION`) is passed to the `bw` process only; without it the vault
  * reports locked.
  */
final case class BitwardenConfig(cli: String, session: Option[Secret], field: String, timeoutSeconds: Int)
    derives ConfigReader
