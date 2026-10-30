package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

import scala.concurrent.duration.*

// ── KeePassXC ───────────────────────────────────────────────────────────────────────────────────

/** `keepassxc:<entry>[#<attribute>]`. `database` is the `.kdbx` file; it is unlocked with `key-file`, `password` (a
  * Secret, e.g. `env:KEEPASSXC_PASSWORD` — never a literal you would not put in the file) and/or a YubiKey's
  * challenge-response (`yubikey`: `"<slot>[:<serial>]"` or a list of them, tried in order — see [[YubiKey]]). With no
  * password, `--no-password` is passed: the key file and/or the YubiKey alone must open it. `ykman` (a name on the PATH
  * or a full path; `""` = none) tells which keys are connected, so only those are tried.
  */
final case class KeePassXcConfig(
    cli: String,
    database: String,
    keyFile: String,
    password: Option[Secret],
    attribute: String,
    timeoutSeconds: Int,
    yubikey: Option[YubiKey.Setting],
    yubikeyTimeoutSeconds: Int,
    ykman: String
) derives ConfigReader
