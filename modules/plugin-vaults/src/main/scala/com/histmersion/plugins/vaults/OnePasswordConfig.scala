package com.histmersion
package plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

import scala.concurrent.duration.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

// ── 1Password ───────────────────────────────────────────────────────────────────────────────────

/** `1password:op://<vault>/<item>/<field>` (the `op://` may be left out). The CLI signs in through the desktop app, or
  * with a service account: `service-account-token` (a Secret) becomes `OP_SERVICE_ACCOUNT_TOKEN` for the `op` process
  * only.
  */
final case class OnePasswordConfig(
    cli: OnePasswordConfig.Cli,
    account: OnePasswordConfig.Account,
    serviceAccountToken: Option[Secret],
    timeoutSeconds: OnePasswordConfig.TimeoutSeconds
) derives ConfigReader

object OnePasswordConfig:
  type Cli = Cli.T
  object Cli extends RefinedType[String, Pure]
  type Account = Account.T
  object Account extends RefinedType[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends RefinedType[Int, Pure]
