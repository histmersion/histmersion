package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

import scala.concurrent.duration.*

// ── Bitwarden ───────────────────────────────────────────────────────────────────────────────────

/** `bitwarden:<item id or search>[#<field>]` (`field` defaults to `password`). `bw` reads an unlocked vault through
  * `BW_SESSION`: `session` (a Secret, e.g. `env:BW_SESSION`) is passed to the `bw` process only; without it the vault
  * reports locked.
  */
final case class BitwardenConfig(cli: BitwardenConfig.Cli, session: Option[Secret], field: BitwardenConfig.Field, timeoutSeconds: BitwardenConfig.TimeoutSeconds)
    derives ConfigReader

object BitwardenConfig:
  type Cli = Cli.T
  object Cli extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Field = Field.T
  object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
