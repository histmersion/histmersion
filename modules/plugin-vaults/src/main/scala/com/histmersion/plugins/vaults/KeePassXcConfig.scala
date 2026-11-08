package com.histmersion
package plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

import scala.concurrent.duration.*
import io.github.iltotore.iron.constraint.any.Pure

// ── KeePassXC ───────────────────────────────────────────────────────────────────────────────────

/** `keepassxc:<entry>[#<attribute>]`. `database` is the `.kdbx` file; it is unlocked with `key-file`, `password` (a
  * Secret, e.g. `env:KEEPASSXC_PASSWORD` — never a literal you would not put in the file) and/or a YubiKey's
  * challenge-response (`yubikey`: `"<slot>[:<serial>]"` or a list of them, tried in order — see [[YubiKey]]). With no
  * password, `--no-password` is passed: the key file and/or the YubiKey alone must open it. `ykman` (a name on the PATH
  * or a full path; `""` = none) tells which keys are connected, so only those are tried.
  */
final case class KeePassXcConfig(
    cli: KeePassXcConfig.Cli,
    database: KeePassXcConfig.Database,
    keyFile: KeePassXcConfig.KeyFile,
    password: Option[Secret],
    attribute: KeePassXcConfig.Attribute,
    timeoutSeconds: KeePassXcConfig.TimeoutSeconds,
    yubikey: Option[YubiKey.Setting],
    yubikeyTimeoutSeconds: KeePassXcConfig.YubikeyTimeoutSeconds,
    ykman: KeePassXcConfig.Ykman
) derives ConfigReader

object KeePassXcConfig:
  type Cli = Cli.T
  object Cli extends Newtype[String, Pure]
  type Database = Database.T
  object Database extends Newtype[String, Pure]
  type KeyFile = KeyFile.T
  object KeyFile extends Newtype[String, Pure]
  type Attribute = Attribute.T
  object Attribute extends Newtype[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
  type YubikeyTimeoutSeconds = YubikeyTimeoutSeconds.T
  object YubikeyTimeoutSeconds extends Newtype[Int, Pure]
  type Ykman = Ykman.T
  object Ykman extends Newtype[String, Pure]
