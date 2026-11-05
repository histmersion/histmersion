package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

/** The first-party secret vaults (docs/research-plugins.md §3.7): each a plugin, built into the server and DISABLED
  * until `histmersion.plugins.<id>.enabled = true`. Each one runs the vault's own CLI as a subprocess ([[Subprocess]])
  * and registers a [[Secret.Vault]] under its id, so `<id>:<ref>` references in any plugin's config resolve through it.
  * No master password is ever stored: a tool that needs one to unlock gets it from a [[Secret]] (`env:…`), or the vault
  * answers that it is locked.
  */
object Vaults:
  /** The version these plugins report (they ship with the server, and change with it). */
  val Version = "0.1.0"

  /** The vault's own settings, shared by all three. */
  private[vaults] def timeout(seconds: Int): FiniteDuration = seconds.max(1).seconds

  /** Pure: `<entry>#<field>`, the field defaulting to `dflt` (split at the LAST `#`). */
  def entryAndField(ref: String, dflt: String): (String, String) =
    ref.lastIndexOf('#') match
      case -1 => (ref.trim, dflt)
      case i  => (ref.take(i).trim, Option(ref.drop(i + 1).trim).filter(_.nonEmpty).getOrElse(dflt))

  /** A Secret given in a vault's own settings (its unlock password, its session), resolved through the host; `None`
    * when the setting is `null`.
    */
  private[vaults] def optional(s: Option[Secret], ctx: Plugin.Context): Either[String, Option[String]] =
    s.fold(Right(None))(ctx.secret(_).map(Some(_)))

  /** Pure: `~/x` against HOME. */
  private[vaults] def expand(p: String, home: Option[String]): String =
    home.filter(_ => p == "~" || p.startsWith("~/")).fold(p)(h => h + p.drop(1))

  /** Run the tool and turn its answer into the value or an error naming the reference — never the value. */
  private[vaults] def answer(
      vault: String,
      ref: String,
      r: Either[String, Subprocess.Result],
      classify: String => Option[String]
  ): Either[String, String] =
    r.flatMap { res =>
      if res.exit == 0 then
        val v = Subprocess.value(res.out)
        if v.isEmpty then Left(s"$vault: '$ref' has an empty value") else Right(v)
      else
        val low = res.err.toLowerCase(java.util.Locale.ROOT)
        Left(
          classify(low).getOrElse(s"$vault: could not read '$ref' (exit ${res.exit}: ${Subprocess.problem(res.err)})")
        )
    }
