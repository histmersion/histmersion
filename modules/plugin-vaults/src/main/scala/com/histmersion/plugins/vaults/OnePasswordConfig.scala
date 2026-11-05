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
    cli: OnePasswordConfig.Cli,
    account: OnePasswordConfig.Account,
    serviceAccountToken: Option[Secret],
    timeoutSeconds: OnePasswordConfig.TimeoutSeconds
) derives ConfigReader

object OnePasswordConfig:
  type Cli = Cli.T
  object Cli extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Account = Account.T
  object Account extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
