package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

import scala.concurrent.duration.*

// ── 1Password ───────────────────────────────────────────────────────────────────────────────────

/** `1password:op://<vault>/<item>/<field>` (the `op://` may be left out). The CLI signs in through the desktop app, or
  * with a service account: `service-account-token` (a Secret) becomes `OP_SERVICE_ACCOUNT_TOKEN` for the `op` process
  * only.
  */
final case class OnePasswordConfig(
    cli: String,
    account: String,
    serviceAccountToken: Option[Secret],
    timeoutSeconds: Int
) derives ConfigReader
